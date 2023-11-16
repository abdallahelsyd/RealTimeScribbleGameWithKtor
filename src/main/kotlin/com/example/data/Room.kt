package com.example.data

import com.example.DrawingServer
import com.example.data.Room.Phase.*
import com.example.data.models.Announcement
import com.example.data.models.ChosenWord
import com.example.data.models.PhaseChange
import com.example.gson
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*

class Room(
    val name:String,
    val maxPlayer: Int,
    var players:List<Player> = listOf()
) {

    private var timerJob:Job?=null
    private var drawingPlayer:Player?=null
    private var winningPlayer= listOf<String>()
    private var word:String?=null

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
        val player=Player(userName,socket,clientId,)
        players=players+player
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
        broadcast(gson.toJson(announcement))
        return player
    }
    private fun timeAndNotify(ms:Long){
        timerJob?.cancel()
        timerJob=GlobalScope.launch {
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

    }
    private fun gameRunning(){

    }
    private fun showWord(){
        GlobalScope.launch {
            if (winningPlayer.isEmpty()){
                drawingPlayer?.let {
                    it.score-= PENALTY_NOBODY_GUESSED_IT
                }
            }
            word?.let {
                val chosenWord =ChosenWord(it,name)
                broadcast(gson.toJson(chosenWord))
            }
            timeAndNotify(DELAY_SHOW_WORD_TO_NEW_ROUND)
            val phaseChange=PhaseChange(SHOW_WORD, DELAY_SHOW_WORD_TO_NEW_ROUND)
            broadcast(gson.toJson(phaseChange))
        }
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
        const val DELAY_WAITING_FOR_START_TO_NEW_ROUND = 10000L
        const val DELAY_NEW_ROUND_TO_GAME_RUNNING = 20000L
        const val DELAY_GAME_RUNNING_TO_SHOW_WORD = 60000L
        const val DELAY_SHOW_WORD_TO_NEW_ROUND = 10000L
        const val PENALTY_NOBODY_GUESSED_IT=50
    }
}