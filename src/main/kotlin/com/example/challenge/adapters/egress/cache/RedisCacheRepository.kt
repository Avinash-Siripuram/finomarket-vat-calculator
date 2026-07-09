package com.example.challenge.adapters.egress.cache

import com.example.challenge.config.RedisConfig
import com.example.challenge.domain.model.TaxRate
import com.example.challenge.domain.ports.egress.CachePort
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class RedisCacheRepository : CachePort {
    private val logger = LoggerFactory.getLogger(RedisCacheRepository::class.java)
    private val cacheDurationSeconds = 3600 // 1 hour TTL

    private fun buildKey(countryCode: String, category: String): String {
        return "tax_rate:$countryCode:$category"
    }

    override fun getTaxRate(countryCode: String, category: String): TaxRate? {
        if (!RedisConfig.isAvailable()) return null
        val key = buildKey(countryCode, category)
        return try {
            RedisConfig.getResource().use { jedis ->
                val json = jedis.get(key)
                if (json != null) {
                    Json.decodeFromString<TaxRate>(json)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn("Redis get operation failed for key {}: {}", key, e.message)
            null
        }
    }

    override fun saveTaxRate(rate: TaxRate) {
        if (!RedisConfig.isAvailable()) return
        val key = buildKey(rate.countryCode, rate.category)
        try {
            val json = Json.encodeToString(rate)
            RedisConfig.getResource().use { jedis ->
                jedis.setex(key, cacheDurationSeconds.toLong(), json)
                logger.info("Saved tax rate to Redis cache with key: {}", key)
            }
        } catch (e: Exception) {
            logger.warn("Redis setex operation failed for key {}: {}", key, e.message)
        }
    }

    override fun evictTaxRate(countryCode: String, category: String) {
        if (!RedisConfig.isAvailable()) return
        val key = buildKey(countryCode, category)
        try {
            RedisConfig.getResource().use { jedis ->
                jedis.del(key)
                logger.info("Evicted tax rate from Redis cache with key: {}", key)
            }
        } catch (e: Exception) {
            logger.warn("Redis del operation failed for key {}: {}", key, e.message)
        }
    }
}
