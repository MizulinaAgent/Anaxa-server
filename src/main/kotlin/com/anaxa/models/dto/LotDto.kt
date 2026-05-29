package com.anaxa.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class LotRequest(
    val categoryId: Int,
    val title: String,
    val description: String?,
    val price: Double,
    val quantity: Int = 1
)

@Serializable
data class LotUpdateRequest(
    val title: String?,
    val description: String?,
    val price: Double?,
    val quantity: Int?,
    val status: String?
)

@Serializable
data class LotResponse(
    val id: String,
    val seller: UserResponse,
    val categoryId: Int,
    val title: String,
    val description: String?,
    val price: Double,
    val quantity: Int,
    val status: String,
    val createdAt: String
)
