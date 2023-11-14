package com.example.data

import com.example.data.Room.Phase.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.isActive

class Room(
    val name:String,
    val maxPlayer: Int,
    var players:List<Player> = listOf()
) {

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

    private fun waitingForPlayers(){

    }
    private fun waitingForStart(){

    }
    private fun newRound(){

    }
    private fun gameRunning(){

    }
    private fun showWord(){

    }
    enum class Phase{
        WAITING_FOR_PLAYERS,
        WAITING_FOR_START,
        NEW_ROUND,
        GAME_RUNNING,
        SHOW_WORD
    }

}