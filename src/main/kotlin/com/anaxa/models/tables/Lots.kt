package com.anaxa.models.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object Lots : UUIDTable("lots") {
    val sellerId = reference("seller_id", Users, onDelete = ReferenceOption.CASCADE)
    val categoryId = reference("category_id", Categories)
    val title = varchar("title", 256)
    val description = text("description").nullable()
    val price = decimal("price", 10, 2)
    val status = varchar("status", 32).default("active")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}
