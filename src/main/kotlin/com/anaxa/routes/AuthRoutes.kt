package com.anaxa.routes

import com.anaxa.models.dto.*
import com.anaxa.models.tables.Users
import com.anaxa.services.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

fun Route.authRoutes() {
    route("/auth") {
        post("/register") {
            val req = call.receive<RegisterRequest>()
            if (req.email.isBlank() || req.password.length < 6 || req.username.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректные данные"))
                return@post
            }

            val exists = transaction {
                Users.selectAll().where { Users.email eq req.email }.count() > 0
            }
            if (exists) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "Email уже зарегистрирован"))
                return@post
            }

            val userId = transaction {
                Users.insertAndGetId {
                    it[email] = req.email
                    it[passwordHash] = AuthService.hashPassword(req.password)
                    it[username] = req.username
                }
            }

            val row = transaction { Users.selectAll().where { Users.id eq userId }.first() }
            val token = AuthService.generateToken(userId.value.toString())
            call.respond(HttpStatusCode.Created, LoginResponse(token, row.toUserResponse()))
        }

        post("/login") {
            val req = call.receive<LoginRequest>()

            val row = transaction { Users.selectAll().where { Users.email eq req.email }.firstOrNull() }
            if (row == null || !AuthService.checkPassword(req.password, row[Users.passwordHash])) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Неверный email или пароль"))
                return@post
            }

            val token = AuthService.generateToken(row[Users.id].value.toString())
            call.respond(LoginResponse(token, row.toUserResponse()))
        }

        authenticate("jwt") {
            get("/me") {
                val userId = call.userId()
                val row = transaction { Users.selectAll().where { Users.id eq userId }.firstOrNull() }
                    ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Пользователь не найден"))
                call.respond(row.toUserResponse())
            }
        }
    }
}

fun ApplicationCall.userId(): UUID =
    UUID.fromString(principal<JWTPrincipal>()!!.payload.getClaim("userId").asString())

fun ResultRow.toUserResponse() = UserResponse(
    id = this[Users.id].value.toString(),
    email = this[Users.email],
    username = this[Users.username],
    avatarUrl = this[Users.avatarUrl],
    rating = this[Users.rating].toDouble(),
    createdAt = this[Users.createdAt].toString()
)
