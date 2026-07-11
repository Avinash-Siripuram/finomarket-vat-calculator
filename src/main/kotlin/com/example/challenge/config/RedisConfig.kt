package com.example.challenge.config

import io.ktor.server.config.*
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

object RedisConfig {
    private val logger = LoggerFactory.getLogger(RedisConfig::class.java)
    private var pool: JedisPool? = null

    fun init(config: ApplicationConfig) {
        val host = config.property("redis.host").getString()
        val port = config.property("redis.port").getString().toInt()
        val password = config.propertyOrNull("redis.password")?.getString()?.takeIf { it.isNotBlank() }

        logger.info("Initializing Redis pool at {}:{} (auth: {})", host, port, if (password != null) "enabled" else "disabled")

        val poolConfig = JedisPoolConfig().apply {
            maxTotal = 20
            maxIdle = 10
            minIdle = 2
            testOnBorrow = true
            testOnReturn = true
        }

        try {
            // Managed Redis (Railway, etc.) rejects unauthenticated commands with NOAUTH
            pool = if (password != null) {
                JedisPool(poolConfig, host, port, 2000, password)
            } else {
                JedisPool(poolConfig, host, port, 2000)
            }
            // Test connection
            getResource().use { jedis ->
                val ping = jedis.ping()
                logger.info("Connected to Redis. Ping response: {}", ping)
            }
        } catch (e: Exception) {
            logger.warn("Could not connect to Redis: {}. Cache-aside will be bypassed.", e.message)
        }
    }

    fun getResource(): Jedis {
        return pool?.resource ?: throw IllegalStateException("Redis pool is not initialized or unavailable")
    }

    fun isAvailable(): Boolean {
        return try {
            pool?.resource?.use { it.ping() == "PONG" } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun close() {
        pool?.close()
    }
}
