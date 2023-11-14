package com.example.plugins

import com.example.plugins.session.DrawingSession
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*

fun Application.configureSession() {
    install(Sessions){
        cookie<DrawingSession>("SESSION")
    }
}