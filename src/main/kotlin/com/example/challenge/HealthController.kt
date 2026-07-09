package com.example.challenge

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
class HealthController(private val jdbcTemplate: JdbcTemplate) {

    @GetMapping("/health")
    fun health(): HealthStatus {
        return try {
            val dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM health_check ORDER BY checked_at DESC LIMIT 1",
                String::class.java
            ) ?: "UNKNOWN"
            HealthStatus("UP", "Database connection OK (Status: $dbStatus)", LocalDateTime.now())
        } catch (e: Exception) {
            HealthStatus("DOWN", "Database error: ${e.message}", LocalDateTime.now())
        }
    }
}

data class HealthStatus(
    val status: String,
    val details: String,
    val timestamp: LocalDateTime
)
