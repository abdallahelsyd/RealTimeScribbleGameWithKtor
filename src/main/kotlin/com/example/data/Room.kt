package com.example.data

import com.example.data.Room.Phase.*
import com.example.data.models.*
import com.example.gson
import com.example.other.getRandomWords
import com.example.other.matchesWord
import com.example.other.transformToUnderscores
import com.example.other.words
import com.example.server
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class Room(
    val name:String,
    val maxPlayer: Int,
    var players:List<Player> = listOf()
) {

    private var timerJob:Job?=null
    private var drawingPlayer:Player?=null
    private var winningPlayers= listOf<String>()
    private var word:String?=null
    private var curWords:List<String>?=null
    private var drawingPlayerIndex=0
    private var startTime =0L
    private val playerRemoveJobs =ConcurrentHashMap<String,Job>()
    private val leftPlayers = ConcurrentHashMap<String,Pair<Player,Int>>()

    private var phaseChangeListener: ((Phase)->Unit)?=null
    var phase= WAITING_FOR_PLAYERS
        set(value) {
            synchronized(field){
                field=value
                phaseChangeListener?.let { change->
                    change(value)
                }
            }
        }

    private fun setPhaseChangeListener(listener:(Phase)->Unit){
        phaseChangeListener=listener
    }

    init {
        setPhaseChangeListener {
            when(it){
                WAITING_FOR_PLAYERS -> waitingForPlayers()
                WAITING_FOR_START -> waitingForStart()
                NEW_ROUND -> newRound()
                GAME_RUNNING -> gameRunning()
                SHOW_WORD -> showWord()
            }
        }
    }

    suspend fun addPlayer(clientId: String,userName: String,socket: WebSocketSession):Player{
        var indexToAdd = players.size-1
        val player=if (leftPlayers.contains(clientId)){
            val leftPlayer=leftPlayers[clientId]
            leftPlayer?.first?.let {
                it.socket=socket
                it.isDrawing=drawingPlayer?.clientId==clientId
                indexToAdd=leftPlayer.second
                playerRemoveJobs[clientId]?.cancel()
                playerRemoveJobs.remove(clientId)
                leftPlayers.remove(clientId)
                it
            }?:Player(userName,socket,clientId)
        } else Player(userName,socket,clientId)

        indexToAdd = when{
            players.isEmpty()-> 0
            indexToAdd >= players.size-> players.size-1
            else -> indexToAdd

        }
        val tmpPlayers = players.toMutableList()
        tmpPlayers.add(indexToAdd,player)
        players =tmpPlayers.toList()

        if (players.size==1)
            phase=WAITING_FOR_PLAYERS
        else if (players.size==2 && phase==WAITING_FOR_PLAYERS) {
            phase = WAITING_FOR_START
            players=players.shuffled()
        }else if (phase==WAITING_FOR_START&&players.size==maxPlayer){
            phase=NEW_ROUND
            players=players.shuffled()
        }
        val announcement=Announcement(
            "$userName joined the room",
            System.currentTimeMillis(),
            Announcement.TYPE_PLAYER_JOINED
        )
        sendWordToPlayer(player)
        broadcastPlayerStats()
        broadcast(gson.toJson(announcement))
        return player
    }
    fun removePlayer(clientId: String){
        val player =players.find { it.clientId==clientId }?:return
        val index = players.indexOf(player)
        leftPlayers[clientId] = player to index
        players = players - player

        playerRemoveJobs[clientId]=GlobalScope.launch {
            delay(PLAYER_REMOVE_TIME)
            val playerToRemove = leftPlayers[clientId]
            leftPlayers.remove(clientId)
            playerToRemove?.let {
                players =players - it.first
            }
            playerRemoveJobs.remove(clientId)
        }
        val announcement =Announcement(
            "${player.userName} left the party :(",
            System.currentTimeMillis(),
            Announcement.TYPE_PLAYER_LEFT
        )
        GlobalScope.launch {
            broadcastPlayerStats()
            broadcast(gson.toJson(announcement))
            if (players.size==1){
                phase=WAITING_FOR_PLAYERS
                timerJob?.cancel()
            }else{
                kill()
                server.rooms.remove(name)
            }
        }
    }
    private fun kill(){
        playerRemoveJobs.values.forEach {
            it.cancel()
            timerJob?.cancel()
        }
    }
    private fun timeAndNotify(ms:Long){
        timerJob?.cancel()
        timerJob=GlobalScope.launch {
            startTime=System.currentTimeMillis()
            val phaseChange=PhaseChange(phase,ms,drawingPlayer?.userName)
            repeat((ms/ UPDATE_TIME_FREQUENCY).toInt()){
                if (it!=0){
                    phaseChange.phase=null
                }
                broadcast(gson.toJson(phaseChange))
                phaseChange.time-= UPDATE_TIME_FREQUENCY
                delay(UPDATE_TIME_FREQUENCY)
            }
            phase= when(phase){
                WAITING_FOR_START->NEW_ROUND
                GAME_RUNNING->SHOW_WORD
                SHOW_WORD->NEW_ROUND
                NEW_ROUND->GAME_RUNNING
                else->WAITING_FOR_PLAYERS
            }
        }
    }
    suspend fun broadcast(msg:String){
        players.forEach {
            if (it.socket.isActive)
                it.socket.send(Frame.Text(msg))
        }
    }
    suspend fun broadcastToAllExcept(msg:String,clientId:String){
        players.forEach {
            if (it.socket.isActive&& it.clientId!=clientId)
                it.socket.send(Frame.Text(msg))
        }
    }
    fun containsPlayer(userName:String):Boolean{
        return players.find { it.userName==userName } !=null
    }

    fun setWordAndSwitchToGameRunning(word: String){
        this.word=word
        phase=GAME_RUNNING

    }

    private fun waitingForPlayers(){
        GlobalScope.launch {
            val phaseChange=PhaseChange(WAITING_FOR_PLAYERS,DELAY_WAITING_FOR_START_TO_NEW_ROUND)
            broadcast(gson.toJson(phaseChange))
        }
    }
    private fun waitingForStart(){
        GlobalScope.launch {
            timeAndNotify(DELAY_WAITING_FOR_START_TO_NEW_ROUND)
            val phaseChange=PhaseChange(WAITING_FOR_START,DELAY_WAITING_FOR_START_TO_NEW_ROUND)
            broadcast(gson.toJson(phaseChange))
        }
    }
    private fun newRound(){
        curWords= getRandomWords(3)
        val newWords =NewWords(curWords!!)
        nextDrawingPlayer()
        GlobalScope.launch {
            broadcastPlayerStats()
            drawingPlayer?.socket?.send(Frame.Text(gson.toJson(newWords)))
            timeAndNotify(DELAY_NEW_ROUND_TO_GAME_RUNNING)
        }
    }
    private fun isGussCorrect(guess:ChatMassage):Boolean{
        return guess.matchesWord(word?:return false)
                && !winningPlayers.contains(guess.from)
                && guess.from!=drawingPlayer?.userName
                && phase==GAME_RUNNING
    }
    private fun gameRunning(){
        winningPlayers= listOf()
        val wordToSend=word?:curWords?.random()?: words.random()
        val wordWithUnderscores=wordToSend.transformToUnderscores()
        val drawingUserName=(drawingPlayer?:players.random()).userName
        val gameStateForDrawingPlayer=GameState(drawingUserName,wordToSend)
        val gameStateForGuessingPlayer=GameState(drawingUserName,wordWithUnderscores)
        GlobalScope.launch {
            broadcastToAllExcept(
                gson.toJson(gameStateForGuessingPlayer),
                drawingPlayer?.clientId?:players.random().clientId
            )
            drawingPlayer?.socket?.send(Frame.Text(gson.toJson(gameStateForDrawingPlayer)))
            timeAndNotify(DELAY_GAME_RUNNING_TO_SHOW_WORD)
            println("Drawing phase in room $name started. It'll last ${DELAY_GAME_RUNNING_TO_SHOW_WORD / 1000}s")
        }
    }
    private fun showWord(){
        GlobalScope.launch {
            if (winningPlayers.isEmpty()){
                drawingPlayer?.let {
                    it.score-= PENALTY_NOBODY_GUESSED_IT
                }
            }
            broadcastPlayerStats()
            word?.let {
                val chosenWord =ChosenWord(it,name)
                broadcast(gson.toJson(chosenWord))
            }
            timeAndNotify(DELAY_SHOW_WORD_TO_NEW_ROUND)
            val phaseChange=PhaseChange(SHOW_WORD, DELAY_SHOW_WORD_TO_NEW_ROUND)
            broadcast(gson.toJson(phaseChange))
        }
    }

    private fun addWinningPlayer(userName: String):Boolean{
        winningPlayers =winningPlayers+userName
        if (winningPlayers.size==players.size-1){
            phase=NEW_ROUND
            return true
        }
        return false
    }

    private suspend fun broadcastPlayerStats(){
        val playersList = players.sortedByDescending { it.score }.map { 
            PlayerData(it.userName,it.isDrawing,it.rank) 
        }
        playersList.forEachIndexed { index, playerData ->
            playerData.rank=index+1
        }
        broadcast(gson.toJson(PlayersList(playersList)))
    }
    private suspend fun sendWordToPlayer(player: Player){
        val delay =when(phase){
            WAITING_FOR_START -> DELAY_WAITING_FOR_START_TO_NEW_ROUND
            NEW_ROUND -> DELAY_NEW_ROUND_TO_GAME_RUNNING
            GAME_RUNNING -> DELAY_GAME_RUNNING_TO_SHOW_WORD
            SHOW_WORD -> DELAY_SHOW_WORD_TO_NEW_ROUND
            else -> 0L
        }
        val phaseChange=PhaseChange(phase,delay,drawingPlayer?.userName)
        word?.let {
            drawingPlayer?.let { player->
                val gameState=GameState(
                    player.userName,
                    if (player.isDrawing || phase==SHOW_WORD) it
                    else it.transformToUnderscores()
                )
                player.socket.send(Frame.Text(gson.toJson(gameState)))
            }
        }
        player.socket.send(Frame.Text(gson.toJson(phaseChange)))
    }
    suspend fun checkWordAndNotifyPlayers(msg: ChatMassage):Boolean{
        if (isGussCorrect(msg)){
            val guessingTime =System.currentTimeMillis()-startTime
            val timePercentageLeft = 1f -guessingTime.toFloat()/ DELAY_GAME_RUNNING_TO_SHOW_WORD
            val score = GUESS_SCORE_DEFAULT+ GUESS_SCORE_PERCENTAGE_MULTIPLIER*timePercentageLeft
            val player =players.find { it.userName==msg.from }
            player?.let {
                it.score+=score.toInt()
            }
            drawingPlayer?.let {
                it.score+= GUESS_SCORE_FOR_DRAWING_PLAYER/players.size
            }
            broadcastPlayerStats()
            val announcement = Announcement(
                "${msg.from} has guessed it!",
                System.currentTimeMillis(),
                Announcement.TYPE_PLAYER_GUESSED_WORD
            )
            broadcast(gson.toJson(announcement))
            val isRoundOver = addWinningPlayer(msg.from)
            if(isRoundOver) {
                val roundOverAnnouncement = Announcement(
                    "Everybody guessed it! New round is starting...",
                    System.currentTimeMillis(),
                    Announcement.TYPE_EVERYBODY_GUESSED_IT
                )
                broadcast(gson.toJson(roundOverAnnouncement))
            }
            return true
        }
        return false
    }


    private fun nextDrawingPlayer(){
        drawingPlayer?.isDrawing=false
        if (players.isEmpty())
            return
        drawingPlayer= if (drawingPlayerIndex<=players.size-1){
            players[drawingPlayerIndex]
        }else players.last()
        if (drawingPlayerIndex<players.size-1)
            drawingPlayerIndex++ else drawingPlayerIndex=0
    }
    enum class Phase{
        WAITING_FOR_PLAYERS,
        WAITING_FOR_START,
        NEW_ROUND,
        GAME_RUNNING,
        SHOW_WORD
    }

    companion object{
        const val UPDATE_TIME_FREQUENCY=1000L
        const val PLAYER_REMOVE_TIME=60000L
        const val DELAY_WAITING_FOR_START_TO_NEW_ROUND = 10000L
        const val DELAY_NEW_ROUND_TO_GAME_RUNNING = 20000L
        const val DELAY_GAME_RUNNING_TO_SHOW_WORD = 60000L
        const val DELAY_SHOW_WORD_TO_NEW_ROUND = 10000L
        const val PENALTY_NOBODY_GUESSED_IT=50
        const val GUESS_SCORE_DEFAULT = 50
        const val GUESS_SCORE_PERCENTAGE_MULTIPLIER = 50
        const val GUESS_SCORE_FOR_DRAWING_PLAYER = 50
    }
}