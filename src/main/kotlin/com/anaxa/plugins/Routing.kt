package com.anaxa.plugins

import com.anaxa.routes.authRoutes
import com.anaxa.routes.gameRoutes
import com.anaxa.routes.lotRoutes
import com.anaxa.routes.orderRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        authRoutes()
        gameRoutes()
        lotRoutes()
        orderRoutes()
    }
}
