package com.anaxa.plugins

import com.anaxa.config.Env
import com.anaxa.models.tables.Categories
import com.anaxa.models.tables.Games
import com.anaxa.models.tables.Lots
import com.anaxa.models.tables.Users
import com.anaxa.services.AuthService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
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
        exec("ALTER TABLE orders ADD COLUMN IF NOT EXISTS buyer_read_at TIMESTAMP")
        exec("ALTER TABLE orders ADD COLUMN IF NOT EXISTS seller_read_at TIMESTAMP")
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
        seedGameIcons()
        seedSellersAndLots()
    }

    private fun Transaction.seedGameIcons() {
        val icons = mapOf(
            "Genshin Impact" to "https://upload.wikimedia.org/wikipedia/en/thumb/5/5d/Genshin_Impact_logo.svg/500px-Genshin_Impact_logo.svg.png",
            "Counter-Strike 2" to "https://cdn.cloudflare.steamstatic.com/steam/apps/730/logo.png",
            "Dota 2" to "https://cdn.cloudflare.steamstatic.com/steam/apps/570/logo.png",
            "Brawl Stars" to "https://upload.wikimedia.org/wikipedia/en/thumb/b/b2/Brawl_Stars_logo_2025.svg/500px-Brawl_Stars_logo_2025.svg.png",
            "World of Warcraft" to "https://upload.wikimedia.org/wikipedia/en/thumb/6/65/World_of_Warcraft.png/500px-World_of_Warcraft.png"
        )
        icons.forEach { (name, url) ->
            exec("UPDATE games SET icon_url = '$url' WHERE name = '$name' AND (icon_url IS NULL OR icon_url = '')")
        }
    }

    private fun Transaction.seedSellersAndLots() {
        if (Lots.selectAll().limit(1).count() > 0) return

        val passwordHash = AuthService.hashPassword("password123")

        data class SellerSpec(val email: String, val username: String, val rating: String)
        val sellers = listOf(
            SellerSpec("coins@anaxa.dev", "CoinSeller", "4.70"),
            SellerSpec("skins@anaxa.dev", "SkinTrader", "4.50"),
            SellerSpec("accs@anaxa.dev", "AccountStore", "4.95"),
            SellerSpec("boost@anaxa.dev", "BoostMaster", "4.30"),
            SellerSpec("pro@anaxa.dev", "ProGamer228", "4.80")
        )
        val sellerIds = sellers.associate { spec ->
            spec.username to Users.insertAndGetId {
                it[email] = spec.email
                it[Users.passwordHash] = passwordHash
                it[username] = spec.username
                it[rating] = BigDecimal(spec.rating)
            }
        }

        fun categoryId(game: String, type: String) =
            Categories.innerJoin(Games).selectAll()
                .where { (Games.name eq game) and (Categories.type eq type) }
                .firstOrNull()?.get(Categories.id)

        data class LotSpec(
            val seller: String,
            val game: String,
            val type: String,
            val title: String,
            val description: String,
            val price: String,
            val quantity: Int
        )
        val lots = listOf(
            LotSpec("CoinSeller", "Genshin Impact", "currency", "Кристаллы Genesis ×6480", "Пополнение через вход в аккаунт, 5–15 минут", "1290.00", 30),
            LotSpec("AccountStore", "Genshin Impact", "accounts", "Аккаунт AR58 · 22 легендарных персонажа", "Почта в комплекте, полный доступ", "8900.00", 1),
            LotSpec("BoostMaster", "Genshin Impact", "services", "Прохождение Бездны 12-3 на ★36", "Гарантия результата, 1–2 дня", "1500.00", 10),
            LotSpec("SkinTrader", "Counter-Strike 2", "items", "AWP | Asiimov (Field-Tested)", "Чистая, без наклеек", "3200.00", 3),
            LotSpec("SkinTrader", "Counter-Strike 2", "items", "★ Karambit | Doppler Phase 2", "Редкий нож, трейд-холд снят", "21500.00", 1),
            LotSpec("AccountStore", "Counter-Strike 2", "accounts", "Аккаунт Prime · 1500 часов · ML 5", "Без банов, оригинальная почта", "2700.00", 2),
            LotSpec("ProGamer228", "Dota 2", "items", "Arcana Phantom Assassin", "Подарок через 30 дней дружбы", "1800.00", 5),
            LotSpec("BoostMaster", "Dota 2", "services", "Калибровка MMR до 4000+", "Соло, без читов", "2500.00", 8),
            LotSpec("AccountStore", "Dota 2", "accounts", "Аккаунт 5500 MMR · Immortal", "Полный доступ, смена данных", "6400.00", 1),
            LotSpec("CoinSeller", "Brawl Stars", "currency", "Гемы ×950", "Через вход в Supercell ID", "990.00", 40),
            LotSpec("AccountStore", "Brawl Stars", "accounts", "Аккаунт 45000 кубков · все бойцы", "Привязка к почте", "4200.00", 1),
            LotSpec("CoinSeller", "World of Warcraft", "currency", "Золото 100k · сервер RU", "Доставка через аукцион", "850.00", 100),
            LotSpec("BoostMaster", "World of Warcraft", "services", "Прокачка 1–70 любой класс", "Без использования ботов", "3500.00", 6),
            LotSpec("ProGamer228", "World of Warcraft", "items", "Подбор маунта Invincible", "Помощь в фарме рейда", "5000.00", 2)
        )

        lots.forEach { spec ->
            val category = categoryId(spec.game, spec.type) ?: return@forEach
            val seller = sellerIds[spec.seller] ?: return@forEach
            Lots.insert {
                it[sellerId] = seller
                it[categoryId] = category
                it[title] = spec.title
                it[description] = spec.description
                it[price] = BigDecimal(spec.price)
                it[quantity] = spec.quantity
            }
        }
    }
}
