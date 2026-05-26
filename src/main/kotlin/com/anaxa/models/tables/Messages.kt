package com.anaxa.models.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object Messages : UUIDTable("messages") {
    val orderId = reference("order_id", Orders, onDelete = ReferenceOption.CASCADE)
    val senderId = reference("sender_id", Users)
    val content = text("content")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}
