package com.anaxa.routes

import com.anaxa.models.dto.ReviewRequest
import com.anaxa.models.dto.ReviewResponse
import com.anaxa.models.tables.Orders
import com.anaxa.models.tables.Reviews
import com.anaxa.models.tables.Users
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

fun Route.reviewRoutes() {
    route("/reviews") {
        authenticate("jwt") {
            post {
                val userId = call.userId()
                val req = call.receive<ReviewRequest>()

                if (req.rating !in 1..5) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Рейтинг должен быть от 1 до 5"))
                }

                val orderId = runCatching { UUID.fromString(req.orderId) }.getOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный orderId"))
                val revieweeId = runCatching { UUID.fromString(req.revieweeId) }.getOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный revieweeId"))

                val reviewId = transaction {
                    val order = Orders.selectAll().where { Orders.id eq orderId }.firstOrNull()
                        ?: return@transaction null
                    val isBuyer = order[Orders.buyerId].value == userId
                    val isSeller = order[Orders.sellerId].value == userId
                    if (!isBuyer && !isSeller) return@transaction null
                    if (order[Orders.status] != "completed") return@transaction null

                    val alreadyReviewed = Reviews.selectAll()
                        .where { (Reviews.orderId eq orderId) and (Reviews.reviewerId eq userId) }
                        .count() > 0
                    if (alreadyReviewed) return@transaction null

                    val newId = Reviews.insertAndGetId {
                        it[Reviews.orderId] = orderId
                        it[Reviews.reviewerId] = userId
                        it[Reviews.revieweeId] = revieweeId
                        it[Reviews.rating] = req.rating
                        it[Reviews.comment] = req.comment
                    }

                    val avg = Reviews.selectAll()
                        .where { Reviews.revieweeId eq revieweeId }
                        .map { it[Reviews.rating] }
                        .average()
                    Users.update({ Users.id eq revieweeId }) {
                        it[Users.rating] = BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP)
                    }

                    newId
                } ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Невозможно оставить отзыв"))

                val review = transaction {
                    val row = Reviews.selectAll().where { Reviews.id eq reviewId }.first()
                    val reviewer = Users.selectAll().where { Users.id eq row[Reviews.reviewerId].value }.first()
                    val reviewee = Users.selectAll().where { Users.id eq row[Reviews.revieweeId].value }.first()
                    ReviewResponse(
                        id = row[Reviews.id].value.toString(),
                        orderId = row[Reviews.orderId].value.toString(),
                        reviewer = reviewer.toUserResponse(),
                        reviewee = reviewee.toUserResponse(),
                        rating = row[Reviews.rating],
                        comment = row[Reviews.comment],
                        createdAt = row[Reviews.createdAt].toString()
                    )
                }
                call.respond(HttpStatusCode.Created, review)
            }
        }
    }

    get("/users/{userId}/reviews") {
        val targetId = runCatching { UUID.fromString(call.parameters["userId"]) }.getOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный userId"))

        val reviews = transaction {
            Reviews.selectAll().where { Reviews.revieweeId eq targetId }
                .orderBy(Reviews.createdAt, SortOrder.DESC)
                .map { row ->
                    val reviewer = Users.selectAll().where { Users.id eq row[Reviews.reviewerId].value }.first()
                    val reviewee = Users.selectAll().where { Users.id eq row[Reviews.revieweeId].value }.first()
                    ReviewResponse(
                        id = row[Reviews.id].value.toString(),
                        orderId = row[Reviews.orderId].value.toString(),
                        reviewer = reviewer.toUserResponse(),
                        reviewee = reviewee.toUserResponse(),
                        rating = row[Reviews.rating],
                        comment = row[Reviews.comment],
                        createdAt = row[Reviews.createdAt].toString()
                    )
                }
        }
        call.respond(reviews)
    }
}
