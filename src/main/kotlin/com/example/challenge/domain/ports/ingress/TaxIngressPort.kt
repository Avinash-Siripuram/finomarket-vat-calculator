package com.example.challenge.domain.ports.ingress

import com.example.challenge.domain.model.AuditLog
import com.example.challenge.domain.model.BatchValidationRequest
import com.example.challenge.domain.model.BatchValidationResult
import com.example.challenge.domain.model.TaxCalculationRequest
import com.example.challenge.domain.model.TaxCalculationResult

interface TaxIngressPort {
    /** Core Requirement 1: cross-border tax calculation for a single transaction. */
    fun calculateTax(requestId: String, request: TaxCalculationRequest): TaxCalculationResult

    /** Core Requirement 2: audit a batch of historical transactions. */
    fun validateBatch(requestId: String, request: BatchValidationRequest): BatchValidationResult

    fun createAuditLog(requestId: String, rawPayload: String): AuditLog
    fun finalizeAuditLog(requestId: String, isSuccess: Boolean, errorMsg: String? = null)
}
