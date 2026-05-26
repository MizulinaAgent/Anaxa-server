package com.anaxa.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class MessageRequest(val content: String)

@Serializable
data class MessageResponse(
    val id: String,
    val orderId: String,
    val sender: UserResponse,
    val content: String,
    val createdAt: String
)

@Serializable
data class ReviewRequest(
    val orderId: String,
    val revieweeId: String,
    val rating: Int,
    val comment: String?
)

@Serializable
data class ReviewResponse(
    val id: String,
    val orderId: String,
    val reviewer: UserResponse,
    val reviewee: UserResponse,
    val rating: Int,
    val comment: String?,
    val createdAt: String
)
