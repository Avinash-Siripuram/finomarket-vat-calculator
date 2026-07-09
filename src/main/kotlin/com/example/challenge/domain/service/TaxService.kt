package com.example.challenge.domain.service

import com.example.challenge.domain.exceptions.DomainException
import com.example.challenge.domain.model.*
import com.example.challenge.domain.ports.ingress.TaxIngressPort
import com.example.challenge.domain.ports.egress.CachePort
import com.example.challenge.domain.ports.egress.TaxRepositoryPort
import org.slf4j.LoggerFactory

class TaxService(
    private val repositoryPort: TaxRepositoryPort,
    private val cachePort: CachePort
) : TaxIngressPort {

    private val logger = LoggerFactory.getLogger(TaxService::class.java)

    override fun calculateTax(requestId: String, request: TaxRequest): TaxResult {
        logger.info("[Request: {}] Processing tax calculation for country: {}, category: {}, amount: {}", 
            requestId, request.countryCode, request.category, request.amount)

        // Cache-Aside lookup
        val taxRate = getTaxRateWithCacheAside(request.countryCode, request.category)

        val taxAmount = request.amount * (taxRate.ratePercentage / 100.0)
        val totalAmount = request.amount + taxAmount

        return TaxResult(
            amount = request.amount,
            taxRate = taxRate.ratePercentage,
            taxAmount = taxAmount,
            totalAmount = totalAmount,
            countryCode = request.countryCode,
            category = request.category
        )
    }

    override fun createAuditLog(requestId: String, rawPayload: String): AuditLog {
        val auditLog = AuditLog(
            requestId = requestId,
            payload = rawPayload,
            status = AuditStatus.PENDING
        )
        return repositoryPort.saveAuditLog(auditLog)
    }

    override fun finalizeAuditLog(requestId: String, isSuccess: Boolean, errorMsg: String?) {
        val status = if (isSuccess) AuditStatus.SUCCESS else AuditStatus.FAILED
        logger.info("[Request: {}] Finalizing audit status to: {}", requestId, status)
        repositoryPort.updateAuditStatus(requestId, status, errorMsg)
    }

    private fun getTaxRateWithCacheAside(countryCode: String, category: String): TaxRate {
        // 1. Try cache
        try {
            val cachedRate = cachePort.getTaxRate(countryCode, category)
            if (cachedRate != null) {
                logger.info("Cache HIT for tax rate: country={}, category={}", countryCode, category)
                return cachedRate
            }
        } catch (e: Exception) {
            logger.warn("Cache lookup failed (Redis connection issue?): {}", e.message)
        }

        // 2. Cache miss -> Database
        logger.info("Cache MISS for tax rate: country={}, category={}. Querying database.", countryCode, category)
        val dbRate = repositoryPort.getTaxRate(countryCode, category) 
            ?: throw DomainException.RateNotFoundException(countryCode, category)

        // 3. Populate cache asynchronously/safely
        try {
            cachePort.saveTaxRate(dbRate)
        } catch (e: Exception) {
            logger.warn("Failed to update cache: {}", e.message)
        }

        return dbRate
    }
}
