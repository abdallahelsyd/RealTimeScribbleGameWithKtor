package com.example

import com.example.plugins.*
import com.example.plugins.session.DrawingSession
import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.sessions.*
import io.ktor.util.*

fun main() {
    embeddedServer(Netty, port = 8001, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

val server=DrawingServer()
val gson = Gson()
fun Application.module() {
    configureSockets()
    configureSerialization()
    configureMonitoring()
    configureRouting()
    configureSession()

    intercept(ApplicationCallPipeline.Features){
        if (call.sessions.get<DrawingSession>()==null){
            val clientId =call.parameters["client_id"]?:""
            call.sessions.set(DrawingSession(clientId, generateNonce()))
        }
    }
}
