package com.anaxa.routes

import com.anaxa.models.tables.Users
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

fun Route.userRoutes() {
    get("/users/{id}") {
        val id = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный id"))

        val row = transaction { Users.selectAll().where { Users.id eq id }.firstOrNull() }
            ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Пользователь не найден"))

        call.respond(row.toUserResponse())
    }
}
