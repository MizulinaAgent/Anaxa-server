package com.anaxa.routes

import com.anaxa.models.dto.LotResponse
import com.anaxa.models.dto.OrderRequest
import com.anaxa.models.dto.OrderResponse
import com.anaxa.models.dto.OrderStatusRequest
import com.anaxa.models.tables.Lots
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

fun Route.orderRoutes() {
    authenticate("jwt") {
        route("/orders") {
            get("/{id}") {
                val userId = call.userId()
                val id = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный id"))

                val order = transaction { buildOrderResponse(id) }
                    ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Заказ не найден"))

                if (order.buyer.id != userId.toString() && order.seller.id != userId.toString()) {
                    return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Нет доступа"))
                }
                call.respond(order)
            }

            post {
                val userId = call.userId()
                val req = call.receive<OrderRequest>()
                val lotId = runCatching { UUID.fromString(req.lotId) }.getOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный lotId"))

                if (req.quantity < 1) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректное количество"))
                }

                val orderId = transaction {
                    val lot = Lots.selectAll().where { Lots.id eq lotId }.firstOrNull()
                        ?: return@transaction null
                    if (lot[Lots.sellerId].value == userId) return@transaction null
                    if (lot[Lots.status] != "active") return@transaction null
                    if (req.quantity > lot[Lots.quantity]) return@transaction null

                    val remaining = lot[Lots.quantity] - req.quantity
                    Lots.update({ Lots.id eq lotId }) {
                        it[quantity] = remaining
                        if (remaining == 0) it[status] = "sold"
                    }

                    Orders.insertAndGetId {
                        it[Orders.lotId] = lotId
                        it[Orders.buyerId] = userId
                        it[Orders.sellerId] = lot[Lots.sellerId].value
                        it[Orders.quantity] = req.quantity
                    }
                } ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Невозможно создать заказ"))

                val order = transaction { buildOrderResponse(orderId.value) }
                    ?: return@post call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Ошибка сервера"))
                call.respond(HttpStatusCode.Created, order)
            }

            get("/my") {
                val userId = call.userId()
                val role = call.request.queryParameters["role"] ?: "buyer"

                val orders = transaction {
                    val condition = if (role == "seller")
                        Op.build { Orders.sellerId eq userId }
                    else
                        Op.build { Orders.buyerId eq userId }

                    Orders.selectAll().where { condition }
                        .orderBy(Orders.createdAt, SortOrder.DESC)
                        .mapNotNull { buildOrderResponse(it[Orders.id].value) }
                }
                call.respond(orders)
            }

            put("/{id}/status") {
                val userId = call.userId()
                val id = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный id"))
                val req = call.receive<OrderStatusRequest>()

                val updated = transaction {
                    val order = Orders.selectAll().where { Orders.id eq id }.firstOrNull()
                        ?: return@transaction null
                    val isBuyer = order[Orders.buyerId].value == userId
                    val isSeller = order[Orders.sellerId].value == userId
                    if (!isBuyer && !isSeller) return@transaction false

                    Orders.update({ Op.build { Orders.id eq id } }) { it[status] = req.status }
                    true
                }

                when (updated) {
                    null -> call.respond(HttpStatusCode.NotFound, mapOf("error" to "Заказ не найден"))
                    false -> call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Нет доступа"))
                    true -> {
                        val order = transaction { buildOrderResponse(id) }
                            ?: return@put call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Ошибка сервера"))
                        call.respond(order)
                    }
                }
            }
        }
    }
}

private fun buildOrderResponse(orderId: UUID): OrderResponse? {
    val order = Orders.selectAll().where { Orders.id eq orderId }.firstOrNull() ?: return null

    val lotRow = Lots.innerJoin(Users, { Lots.sellerId }, { Users.id })
        .selectAll().where { Lots.id eq order[Orders.lotId].value }
        .firstOrNull() ?: return null

    val buyer = Users.selectAll().where { Users.id eq order[Orders.buyerId].value }.firstOrNull() ?: return null
    val seller = Users.selectAll().where { Users.id eq order[Orders.sellerId].value }.firstOrNull() ?: return null

    val lotResponse = LotResponse(
        id = lotRow[Lots.id].value.toString(),
        seller = seller.toUserResponse(),
        categoryId = lotRow[Lots.categoryId].value,
        title = lotRow[Lots.title],
        description = lotRow[Lots.description],
        price = lotRow[Lots.price].toDouble(),
        quantity = lotRow[Lots.quantity],
        status = lotRow[Lots.status],
        createdAt = lotRow[Lots.createdAt].toString()
    )

    return OrderResponse(
        id = order[Orders.id].value.toString(),
        lot = lotResponse,
        buyer = buyer.toUserResponse(),
        seller = seller.toUserResponse(),
        quantity = order[Orders.quantity],
        status = order[Orders.status],
        createdAt = order[Orders.createdAt].toString()
    )
}
