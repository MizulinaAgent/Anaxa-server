package com.anaxa.models.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object Games : IntIdTable("games") {
    val name = varchar("name", 128)
    val iconUrl = text("icon_url").nullable()
    val description = text("description").nullable()
}
