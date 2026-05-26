package com.anaxa.models.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object Reviews : UUIDTable("reviews") {
    val orderId = reference("order_id", Orders)
    val reviewerId = reference("reviewer_id", Users)
    val revieweeId = reference("reviewee_id", Users)
    val rating = integer("rating")
    val comment = text("comment").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}
