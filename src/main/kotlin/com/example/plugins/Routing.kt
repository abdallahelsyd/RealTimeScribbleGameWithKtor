package com.example.plugins

import com.example.routes.createRoomRoute
import com.example.routes.gameWebSocketRoute
import com.example.routes.getRoomsRoute
import com.example.routes.joinRoomRoute
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*

fun Application.configureRouting() {
    install(Routing){
        createRoomRoute()
        getRoomsRoute()
        joinRoomRoute()
        gameWebSocketRoute()
    }
}
