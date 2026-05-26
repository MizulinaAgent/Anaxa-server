package com.anaxa.routes

import com.anaxa.models.dto.CategoryResponse
import com.anaxa.models.dto.GameResponse
import com.anaxa.models.tables.Categories
import com.anaxa.models.tables.Games
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.gameRoutes() {
    route("/games") {
        get {
            val games = transaction {
                Games.selectAll().map {
                    GameResponse(
                        id = it[Games.id].value,
                        name = it[Games.name],
                        iconUrl = it[Games.iconUrl],
                        description = it[Games.description]
                    )
                }
            }
            call.respond(games)
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный id"))

            val game = transaction {
                Games.selectAll().where { Games.id eq id }.firstOrNull()?.let {
                    GameResponse(
                        id = it[Games.id].value,
                        name = it[Games.name],
                        iconUrl = it[Games.iconUrl],
                        description = it[Games.description]
                    )
                }
            } ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Игра не найдена"))

            call.respond(game)
        }

        get("/{gameId}/categories") {
            val gameId = call.parameters["gameId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный gameId"))

            val categories = transaction {
                Categories.selectAll().where { Categories.gameId eq gameId }.map {
                    CategoryResponse(
                        id = it[Categories.id].value,
                        gameId = it[Categories.gameId].value,
                        type = it[Categories.type]
                    )
                }
            }
            call.respond(categories)
        }
    }
}
