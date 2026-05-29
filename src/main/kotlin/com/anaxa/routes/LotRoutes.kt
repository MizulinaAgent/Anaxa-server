package com.anaxa.routes

import com.anaxa.models.dto.LotRequest
import com.anaxa.models.dto.LotResponse
import com.anaxa.models.dto.LotUpdateRequest
import com.anaxa.models.tables.Categories
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
import java.math.BigDecimal
import java.util.UUID

fun Route.lotRoutes() {
    route("/lots") {
        get {
            val categoryId = call.request.queryParameters["categoryId"]?.toIntOrNull()
            val status = call.request.queryParameters["status"]
            val sellerId = call.request.queryParameters["sellerId"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

            val lots = transaction {
                val query = Lots.innerJoin(Users, { Lots.sellerId }, { Users.id }).selectAll()
                if (status != null) query.andWhere { Lots.status eq status }
                if (categoryId != null) query.andWhere { Lots.categoryId eq categoryId }
                if (sellerId != null) query.andWhere { Lots.sellerId eq sellerId }
                query.orderBy(Lots.createdAt, SortOrder.DESC).map { it.toLotResponse() }
            }
            call.respond(lots)
        }

        get("/{id}") {
            val id = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный id"))

            val lot = transaction {
                Lots.innerJoin(Users, { Lots.sellerId }, { Users.id })
                    .selectAll().where { Lots.id eq id }
                    .firstOrNull()?.toLotResponse()
            } ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Лот не найден"))

            call.respond(lot)
        }

        authenticate("jwt") {
            post {
                val userId = call.userId()
                val req = call.receive<LotRequest>()

                if (req.title.isBlank() || req.price <= 0.0 || req.quantity < 1) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректные данные"))
                }

                val lotId = transaction {
                    Lots.insertAndGetId {
                        it[sellerId] = userId
                        it[categoryId] = req.categoryId
                        it[title] = req.title
                        it[description] = req.description
                        it[price] = BigDecimal.valueOf(req.price)
                        it[quantity] = req.quantity
                    }
                }

                val lot = transaction {
                    Lots.innerJoin(Users, { Lots.sellerId }, { Users.id })
                        .selectAll().where { Lots.id eq lotId }
                        .first().toLotResponse()
                }
                call.respond(HttpStatusCode.Created, lot)
            }

            put("/{id}") {
                val userId = call.userId()
                val id = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный id"))
                val req = call.receive<LotUpdateRequest>()

                val updated = transaction {
                    val lot = Lots.selectAll().where { Lots.id eq id }.firstOrNull()
                        ?: return@transaction null
                    if (lot[Lots.sellerId].value != userId) return@transaction false

                    Lots.update({ Lots.id eq id }) {
                        req.title?.let { v -> it[title] = v }
                        req.description?.let { v -> it[description] = v }
                        req.price?.let { v -> it[price] = BigDecimal.valueOf(v) }
                        req.quantity?.let { v -> it[quantity] = v }
                        req.status?.let { v -> it[status] = v }
                    }
                    true
                }

                when (updated) {
                    null -> call.respond(HttpStatusCode.NotFound, mapOf("error" to "Лот не найден"))
                    false -> call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Нет доступа"))
                    true -> {
                        val lot = transaction {
                            Lots.innerJoin(Users, { Lots.sellerId }, { Users.id })
                                .selectAll().where { Lots.id eq id }
                                .first().toLotResponse()
                        }
                        call.respond(lot)
                    }
                }
            }

            delete("/{id}") {
                val userId = call.userId()
                val id = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный id"))

                val result = transaction {
                    val lot = Lots.selectAll().where { Lots.id eq id }.firstOrNull()
                        ?: return@transaction "not_found"
                    if (lot[Lots.sellerId].value != userId) return@transaction "forbidden"
                    val hasOrders = Orders.selectAll().where { Orders.lotId eq id }.count() > 0
                    if (hasOrders) return@transaction "has_orders"
                    Lots.deleteWhere { Op.build { Lots.id eq id } }
                    "deleted"
                }

                when (result) {
                    "not_found" -> call.respond(HttpStatusCode.NotFound, mapOf("error" to "Лот не найден"))
                    "forbidden" -> call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Нет доступа"))
                    "has_orders" -> call.respond(HttpStatusCode.Conflict, mapOf("error" to "Нельзя удалить лот с заказами, скройте его"))
                    else -> call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private fun ResultRow.toLotResponse(): LotResponse {
    val categoryType = Categories.selectAll()
        .where { Categories.id eq this@toLotResponse[Lots.categoryId] }
        .firstOrNull()?.get(Categories.type).orEmpty()
    return LotResponse(
        id = this[Lots.id].value.toString(),
        seller = toUserResponse(),
        categoryId = this[Lots.categoryId].value,
        categoryType = categoryType,
        title = this[Lots.title],
        description = this[Lots.description],
        price = this[Lots.price].toDouble(),
        quantity = this[Lots.quantity],
        status = this[Lots.status],
        createdAt = this[Lots.createdAt].toString()
    )
}
