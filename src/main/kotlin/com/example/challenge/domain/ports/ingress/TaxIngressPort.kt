package com.example.challenge.domain.ports.ingress

import com.example.challenge.domain.model.AuditLog
import com.example.challenge.domain.model.TaxRequest
import com.example.challenge.domain.model.TaxResult

interface TaxIngressPort {
    fun calculateTax(requestId: String, request: TaxRequest): TaxResult
    fun createAuditLog(requestId: String, rawPayload: String): AuditLog
    fun finalizeAuditLog(requestId: String, isSuccess: Boolean, errorMsg: String? = null)
}
