package com.example.routes

import com.example.data.Player
import com.example.data.Room
import com.example.data.models.*
import com.example.gson
import com.example.other.Constants.TYPE_ANNOUNCEMENT
import com.example.other.Constants.TYPE_CHAT_MASSAGE
import com.example.other.Constants.TYPE_CHOSEN_WORD
import com.example.other.Constants.TYPE_DRAW_DATA
import com.example.other.Constants.TYPE_GAME_STATE
import com.example.other.Constants.TYPE_JOIN_ROOM_ERROR
import com.example.other.Constants.TYPE_NEW_WORD
import com.example.other.Constants.TYPE_PHASE_CHANGE
import com.example.plugins.session.DrawingSession
import com.example.server
import com.google.gson.JsonParser
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach




fun Route.gameWebSocketRoute(){
    route("/ws/draw"){
        standerWebSocket { socket, clientId, message, payload ->
            when(payload){
                is DrawData->{
                    val room = server.rooms[payload.roomName]?:return@standerWebSocket
                    if (room.phase ==Room.Phase.GAME_RUNNING){
                        room.broadcastToAllExcept(message,clientId)
                    }
                }
                is ChatMassage->{
                    val room = server.rooms[payload.roomName]?:return@standerWebSocket
                    if (!room.checkWordAndNotifyPlayers(payload)){
                        room.broadcast(message)
                    }
                }
                is Announcement->{

                }
                is JoinRoomHandShake->{
                    val room= server.rooms[payload.roomName]
                    if (room==null){
                        val gameError =GameError(GameError.ERROR_ROOM_NOT_FOUND)
                        socket.send(Frame.Text(gson.toJson(gameError)))
                        return@standerWebSocket
                    }
                    val player=Player(payload.userName,socket,payload.clientId)
                    server.playerJoined(player)
                    if (!room.containsPlayer(player.userName)){
                        room.addPlayer(player.clientId,player.userName,socket)
                    }
                }
                is ChosenWord->{
                    val room= server.rooms[payload.roomName]?: return@standerWebSocket
                    room.setWordAndSwitchToGameRunning(payload.chosenWord)
                }
            }
        }
    }
}
fun Route.standerWebSocket(
    handleFrame: suspend (
        socket:DefaultWebSocketSession,
        clientId:String,
        message:String,
        payload: BaseModel
    )->Unit
){
    webSocket {
        val session =call.sessions.get<DrawingSession>()
        if (session==null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session."))
            return@webSocket
        }
        try {
            incoming.consumeEach {
                if (it is Frame.Text){
                    val message =it.readText()
                    val jsonObject=JsonParser.parseString((message)).asJsonObject
                    val type =when(jsonObject.get("type").asString){
                        TYPE_CHAT_MASSAGE->ChatMassage::class.java
                        TYPE_DRAW_DATA-> DrawData::class.java
                        TYPE_ANNOUNCEMENT -> Announcement::class.java
                        TYPE_JOIN_ROOM_ERROR -> Announcement::class.java
                        TYPE_PHASE_CHANGE -> PhaseChange::class.java
                        TYPE_CHOSEN_WORD -> ChosenWord::class.java
                        TYPE_GAME_STATE -> GameState::class.java
                        TYPE_NEW_WORD -> NewWords::class.java
                        else->BaseModel::class.java
                    }
                    val payload= gson.fromJson(message,type)
                    handleFrame(this,session.clientId,message,payload)

                }
            }
        }catch (e:Exception){
            e.printStackTrace()
        }finally {
            //handle disconnects
        }
    }
}