package com.anaxa.services

import com.anaxa.config.Env
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.mindrot.jbcrypt.BCrypt
import java.util.*

object AuthService {
    fun hashPassword(password: String): String =
        BCrypt.hashpw(password, BCrypt.gensalt())

    fun checkPassword(password: String, hash: String): Boolean =
        BCrypt.checkpw(password, hash)

    fun generateToken(userId: String): String =
        JWT.create()
            .withIssuer(Env.jwtIssuer)
            .withAudience(Env.jwtAudience)
            .withClaim("userId", userId)
            .withExpiresAt(Date(System.currentTimeMillis() + Env.jwtExpirationMs))
            .sign(Algorithm.HMAC256(Env.jwtSecret))
}
