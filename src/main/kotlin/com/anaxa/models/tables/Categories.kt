package com.anaxa.models.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object Categories : IntIdTable("categories") {
    val gameId = reference("game_id", Games, onDelete = ReferenceOption.CASCADE)
    val type = varchar("type", 64)
}
