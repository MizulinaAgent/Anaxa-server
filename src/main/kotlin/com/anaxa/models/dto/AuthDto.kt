package com.anaxa.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val email: String, val password: String, val username: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val username: String,
    val avatarUrl: String?,
    val rating: Double,
    val createdAt: String
)

@Serializable
data class LoginResponse(val token: String, val user: UserResponse)
