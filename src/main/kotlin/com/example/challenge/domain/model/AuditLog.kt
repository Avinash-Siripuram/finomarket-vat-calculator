package com.example.challenge.domain.model

import java.time.LocalDateTime

data class AuditLog(
    val id: Long? = null,
    val requestId: String,
    val payload: String,
    val status: AuditStatus,
    val errorMessage: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class AuditStatus {
    PENDING,
    SUCCESS,
    FAILED
}
