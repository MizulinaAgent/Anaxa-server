package com.anaxa.models.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import java.math.BigDecimal

object Users : UUIDTable("users") {
    val email = varchar("email", 256).uniqueIndex()
    val passwordHash = varchar("password_hash", 256)
    val username = varchar("username", 64)
    val avatarUrl = text("avatar_url").nullable()
    val rating = decimal("rating", 3, 2).default(BigDecimal.ZERO)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}
