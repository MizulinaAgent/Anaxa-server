package com.anaxa

import com.anaxa.config.Env
import com.anaxa.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = Env.port, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureCORS()
    configureStatusPages()
    configureAuthentication()
    DatabaseFactory.init()
    configureRouting()
    routing {
        get("/health") { call.respondText("OK") }
    }
}
