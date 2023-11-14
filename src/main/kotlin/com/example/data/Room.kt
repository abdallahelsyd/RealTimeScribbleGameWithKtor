package com.example.data

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.isActive

class Room(
    val name:String,
    val maxPlayer: Int,
    var players:List<Player> = listOf()
) {
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
}