package com.anaxa.routes

import com.anaxa.models.dto.MessageRequest
import com.anaxa.models.dto.MessageResponse
import com.anaxa.models.tables.Messages
import com.anaxa.models.tables.Orders
import com.anaxa.models.tables.Users
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

fun Route.messageRoutes() {
    authenticate("jwt") {
        route("/orders/{orderId}/messages") {
            get {
                val userId = call.userId()
                val orderId = runCatching { UUID.fromString(call.parameters["orderId"]) }.getOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный orderId"))

                val hasAccess = transaction {
                    val order = Orders.selectAll().where { Orders.id eq orderId }.firstOrNull()
                        ?: return@transaction false
                    order[Orders.buyerId].value == userId || order[Orders.sellerId].value == userId
                }
                if (!hasAccess) return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Нет доступа"))

                val messages = transaction {
                    Messages.innerJoin(Users, { Messages.senderId }, { Users.id })
                        .selectAll().where { Messages.orderId eq orderId }
                        .orderBy(Messages.createdAt, SortOrder.ASC)
                        .map { row ->
                            MessageResponse(
                                id = row[Messages.id].value.toString(),
                                orderId = orderId.toString(),
                                sender = row.toUserResponse(),
                                content = row[Messages.content],
                                createdAt = row[Messages.createdAt].toString()
                            )
                        }
                }
                call.respond(messages)
            }

            post {
                val userId = call.userId()
                val orderId = runCatching { UUID.fromString(call.parameters["orderId"]) }.getOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный orderId"))
                val req = call.receive<MessageRequest>()

                if (req.content.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Сообщение не может быть пустым"))
                }

                val msgId = transaction {
                    val order = Orders.selectAll().where { Orders.id eq orderId }.firstOrNull()
                        ?: return@transaction null
                    val isBuyer = order[Orders.buyerId].value == userId
                    val isSeller = order[Orders.sellerId].value == userId
                    if (!isBuyer && !isSeller) return@transaction null

                    Messages.insertAndGetId {
                        it[Messages.orderId] = orderId
                        it[Messages.senderId] = userId
                        it[Messages.content] = req.content
                    }
                } ?: return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Нет доступа"))

                val msg = transaction {
                    Messages.innerJoin(Users, { Messages.senderId }, { Users.id })
                        .selectAll().where { Messages.id eq msgId }
                        .first().let { row ->
                            MessageResponse(
                                id = row[Messages.id].value.toString(),
                                orderId = orderId.toString(),
                                sender = row.toUserResponse(),
                                content = row[Messages.content],
                                createdAt = row[Messages.createdAt].toString()
                            )
                        }
                }
                call.respond(HttpStatusCode.Created, msg)
            }
        }
    }
}
