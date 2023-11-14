package com.example.routes

import com.example.data.Room
import com.example.data.models.BasicApiResponse
import com.example.data.models.CreateRoomRequest
import com.example.data.models.RoomResponse
import com.example.other.Constants.MAX_ROOM_SIZE
import com.example.server
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*

fun Route.createRoomRoute(){
    route("/api/createRoom"){
        post {
            val roomRequest  = call.receiveOrNull<CreateRoomRequest>()
            if (roomRequest==null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            if (server.rooms[roomRequest.name]!=null){
                call.respond(
                    HttpStatusCode.OK,
                    BasicApiResponse(false,"Room is already exist")
                )
                return@post
            }
            if (roomRequest.maxPlayers<2){
                call.respond(
                    HttpStatusCode.OK,
                    BasicApiResponse(false,"The minimum room size is 2")
                )
                return@post
            }

            if (roomRequest.maxPlayers > MAX_ROOM_SIZE) {
                call.respond(
                    HttpStatusCode.OK,
                    BasicApiResponse(false, "the maximum room size is $MAX_ROOM_SIZE")
                )
                return@post
            }
            val room= Room(
                roomRequest.name,
                roomRequest.maxPlayers
            )
            server.rooms[roomRequest.name] =room
            println("Room Created: ${roomRequest.name}")
            call.respond(HttpStatusCode.OK,BasicApiResponse(true))
        }
    }
}
fun Route.getRoomsRoute(){
    route("/api/getRooms"){
        get {
            val searchQuery =call.parameters["searchQuery"]
            if (searchQuery==null){
                call.respond((HttpStatusCode.BadRequest))
                return@get
            }
            val roomsResult = server.rooms.filterKeys {
                it.contentEquals(searchQuery,true)
            }

            val roomResponse=roomsResult.values.map {
                RoomResponse(it.name,it.maxPlayer,it.players.size)
            }.sortedBy { it.name }

            call.respond(HttpStatusCode.OK,roomResponse)
        }
    }
}

fun Route.joinRoomRoute(){
    route("/api/joinRoom"){
        get {
            val userName=call.parameters["userName"]
            val roomName=call.parameters["roomName"]
            if (userName==null || roomName==null)
                call.respond(HttpStatusCode.BadRequest)
            val room= server.rooms[roomName]
            when{
                room==null->{
                    call.respond(HttpStatusCode.OK,BasicApiResponse(false,"Room is not exist"))
                }

            }
        }

    }
}