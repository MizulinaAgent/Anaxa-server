package com.anaxa.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class GameResponse(
    val id: Int,
    val name: String,
    val iconUrl: String?,
    val description: String?
)

@Serializable
data class CategoryResponse(val id: Int, val gameId: Int, val type: String)
