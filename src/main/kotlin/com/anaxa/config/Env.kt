package com.anaxa.config

import io.github.cdimascio.dotenv.dotenv

object Env {
    private val dotenv = dotenv {
        ignoreIfMissing = true
    }

    private fun get(key: String): String? = dotenv[key] ?: System.getenv(key)

    private fun require(key: String): String =
        get(key) ?: error("Missing required environment variable: $key")

    val databaseUrl: String get() = require("DATABASE_URL")
    val jwtSecret: String get() = require("JWT_SECRET")
    val jwtExpirationMs: Long get() = get("JWT_EXPIRATION_MS")?.toLongOrNull() ?: 86_400_000L
    val jwtIssuer: String get() = get("JWT_ISSUER") ?: "anaxa"
    val jwtAudience: String get() = get("JWT_AUDIENCE") ?: "anaxa-users"
    val port: Int get() = get("PORT")?.toIntOrNull() ?: 8080
}
