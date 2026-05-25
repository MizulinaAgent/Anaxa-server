package com.anaxa.plugins

import com.anaxa.config.Env
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI

object DatabaseFactory {
    fun init() {
        Database.connect(dataSource())
        transaction {
            exec("SELECT 1")
        }
    }

    private fun dataSource(): HikariDataSource {
        val uri = URI(Env.databaseUrl)
        val credentials = uri.userInfo?.split(":", limit = 2)
        val user = credentials?.getOrNull(0).orEmpty()
        val password = credentials?.getOrNull(1).orEmpty()
        val port = if (uri.port != -1) uri.port else 5432
        val database = uri.path.trimStart('/')
        val jdbcUrl = "jdbc:postgresql://${uri.host}:$port/$database?sslmode=require&prepareThreshold=0"

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 5
            minimumIdle = 1
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }
        return HikariDataSource(config)
    }
}
