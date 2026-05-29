package com.anaxa.models.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object Orders : UUIDTable("orders") {
    val lotId = reference("lot_id", Lots)
    val buyerId = reference("buyer_id", Users)
    val sellerId = reference("seller_id", Users)
    val quantity = integer("quantity").default(1)
    val status = varchar("status", 32).default("pending")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}
