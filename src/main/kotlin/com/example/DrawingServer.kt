package com.example

import com.example.data.Player
import com.example.data.Room
import java.util.concurrent.ConcurrentHashMap

class DrawingServer {
    val rooms =ConcurrentHashMap<String,Room>()
    val players =ConcurrentHashMap<String,Player>()

    fun playerJoined(player: Player){
        players[player.clientId]=player
    }

    fun playerLeft(clientId: String,immediatelyDisconnect:Boolean=false){
        val playersRoom =getRoomWithClientId(clientId)
        if (immediatelyDisconnect){
            println("Closing connection to ${players[clientId]?.userName}")
            playersRoom?.removePlayer(clientId)
            players.remove(clientId)
        }

    }
    fun getRoomWithClientId(clientId:String):Room? {
        val filteredRooms=rooms.filterValues {
            it.players.find {player ->
                player.clientId==clientId
            }!=null
        }
        return if (filteredRooms.values.isEmpty())
                null
            else
                filteredRooms.values.toList()[0]
    }
}