package com.example.routes

import com.example.data.models.BaseModel
import com.example.data.models.ChatMassage
import com.example.gson
import com.example.other.Constants.TYPE_CHAT_MASSAGE
import com.example.plugins.session.DrawingSession
import com.google.gson.JsonParser
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach

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