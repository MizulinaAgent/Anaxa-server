package com.anaxa.plugins

import com.anaxa.routes.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        authRoutes()
        userRoutes()
        gameRoutes()
        lotRoutes()
        orderRoutes()
        messageRoutes()
        reviewRoutes()
    }
}
