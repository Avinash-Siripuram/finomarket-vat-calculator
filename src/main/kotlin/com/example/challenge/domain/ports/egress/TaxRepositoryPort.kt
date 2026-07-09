package com.example.challenge.domain.ports.egress

import com.example.challenge.domain.model.AuditLog
import com.example.challenge.domain.model.AuditStatus
import com.example.challenge.domain.model.TaxRate

interface TaxRepositoryPort {
    fun getTaxRate(countryCode: String, category: String): TaxRate?
    fun saveAuditLog(log: AuditLog): AuditLog
    fun updateAuditStatus(requestId: String, status: AuditStatus, errorMessage: String? = null)
}
