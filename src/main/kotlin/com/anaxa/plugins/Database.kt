package com.anaxa.plugins

import com.anaxa.config.Env
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI

object DatabaseFactory {
    fun init() {
        Database.connect(dataSource())
        repeat(5) { attempt ->
            val result = runCatching {
                transaction {
                    createSchema()
                    seed()
                }
            }
            if (result.isSuccess) return
            if (attempt < 4) Thread.sleep(5000L) else result.getOrThrow()
        }
    }

    private fun dataSource(): HikariDataSource {
        val uri = URI(Env.databaseUrl)
        val credentials = uri.userInfo?.split(":", limit = 2)
        val user = credentials?.getOrNull(0).orEmpty()
        val password = credentials?.getOrNull(1).orEmpty()
        val port = if (uri.port != -1) uri.port else 5432
        val database = uri.path.trimStart('/')
        val jdbcUrl = "jdbc:postgresql://${uri.host}:$port/$database?sslmode=require"

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 3
            minimumIdle = 1
            isAutoCommit = false
            connectionInitSql = "SELECT 1"
            connectionTimeout = 30000
        }
        return HikariDataSource(config)
    }

    private fun Transaction.createSchema() {
        exec("""CREATE TABLE IF NOT EXISTS users (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            email VARCHAR(256) UNIQUE NOT NULL,
            password_hash VARCHAR(256) NOT NULL,
            username VARCHAR(64) NOT NULL,
            avatar_url TEXT,
            rating DECIMAL(3,2) DEFAULT 0.0,
            created_at TIMESTAMP DEFAULT NOW()
        )""")
        exec("""CREATE TABLE IF NOT EXISTS games (
            id SERIAL PRIMARY KEY,
            name VARCHAR(128) NOT NULL,
            icon_url TEXT,
            description TEXT
        )""")
        exec("""CREATE TABLE IF NOT EXISTS categories (
            id SERIAL PRIMARY KEY,
            game_id INT REFERENCES games(id) ON DELETE CASCADE,
            type VARCHAR(64) NOT NULL
        )""")
        exec("""CREATE TABLE IF NOT EXISTS lots (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            seller_id UUID REFERENCES users(id) ON DELETE CASCADE,
            category_id INT REFERENCES categories(id),
            title VARCHAR(256) NOT NULL,
            description TEXT,
            price DECIMAL(10,2) NOT NULL,
            quantity INT NOT NULL DEFAULT 1,
            status VARCHAR(32) DEFAULT 'active',
            created_at TIMESTAMP DEFAULT NOW()
        )""")
        exec("""CREATE TABLE IF NOT EXISTS orders (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            lot_id UUID REFERENCES lots(id),
            buyer_id UUID REFERENCES users(id),
            seller_id UUID REFERENCES users(id),
            quantity INT NOT NULL DEFAULT 1,
            status VARCHAR(32) DEFAULT 'pending',
            created_at TIMESTAMP DEFAULT NOW()
        )""")
        exec("ALTER TABLE lots ADD COLUMN IF NOT EXISTS quantity INT NOT NULL DEFAULT 1")
        exec("ALTER TABLE orders ADD COLUMN IF NOT EXISTS quantity INT NOT NULL DEFAULT 1")
        exec("""CREATE TABLE IF NOT EXISTS messages (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            order_id UUID REFERENCES orders(id) ON DELETE CASCADE,
            sender_id UUID REFERENCES users(id),
            content TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT NOW()
        )""")
        exec("""CREATE TABLE IF NOT EXISTS reviews (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            order_id UUID REFERENCES orders(id),
            reviewer_id UUID REFERENCES users(id),
            reviewee_id UUID REFERENCES users(id),
            rating INT CHECK (rating BETWEEN 1 AND 5),
            comment TEXT,
            created_at TIMESTAMP DEFAULT NOW()
        )""")
    }

    private fun Transaction.seed() {
        exec("""
            WITH new_games AS (
                INSERT INTO games (name, description)
                SELECT v.name, v.descr FROM (VALUES
                    ('Genshin Impact',    'Гача-RPG от HoYoverse'),
                    ('Counter-Strike 2', 'Тактический шутер от Valve'),
                    ('Dota 2',           'MOBA от Valve'),
                    ('Brawl Stars',      'Мобильный экшен от Supercell'),
                    ('World of Warcraft','Классическая MMORPG от Blizzard')
                ) AS v(name, descr)
                WHERE NOT EXISTS (SELECT 1 FROM games LIMIT 1)
                RETURNING id
            )
            INSERT INTO categories (game_id, type)
            SELECT g.id, t.type
            FROM new_games g
            CROSS JOIN (VALUES ('currency'),('items'),('accounts'),('services')) AS t(type)
        """)
    }
}
