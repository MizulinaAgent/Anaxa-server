package com.anaxa.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class OrderRequest(val lotId: String)

@Serializable
data class OrderStatusRequest(val status: String)

@Serializable
data class OrderResponse(
    val id: String,
    val lot: LotResponse,
    val buyer: UserResponse,
    val seller: UserResponse,
    val status: String,
    val createdAt: String
)
