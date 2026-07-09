package com.example.challenge.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

object DatabaseConfig {
    private val logger = LoggerFactory.getLogger(DatabaseConfig::class.java)
    private var dataSource: HikariDataSource? = null

    fun init(config: ApplicationConfig) {
        val url = config.property("database.url").getString()
        val user = config.property("database.user").getString()
        val password = config.property("database.password").getString()

        logger.info("Initializing database pool for URL: {}", url)

        val hikariConfig = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = url
            username = user
            this.password = password
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            initializationFailTimeout = 0
            validate()
        }

        dataSource = HikariDataSource(hikariConfig)
        
        // Connect JetBrains Exposed
        Database.connect(dataSource!!)

        // Run Flyway Migrations
        runMigrations()
    }

    private fun runMigrations() {
        logger.info("Running database migrations...")
        try {
            val flyway = Flyway.configure()
                .dataSource(dataSource)
                .load()
            flyway.migrate()
            logger.info("Database migrations completed successfully.")
        } catch (e: Exception) {
            logger.error("Database migration failed", e)
            throw e
        }
    }

    fun close() {
        dataSource?.close()
    }
}
