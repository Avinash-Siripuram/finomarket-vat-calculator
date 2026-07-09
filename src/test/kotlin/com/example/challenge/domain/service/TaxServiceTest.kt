package com.example.challenge.domain.service

import com.example.challenge.domain.exceptions.DomainException
import com.example.challenge.domain.model.*
import com.example.challenge.domain.ports.egress.CachePort
import com.example.challenge.domain.ports.egress.TaxRepositoryPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TaxServiceTest {

    // Simple fakes for testing instead of heavy mocking libraries
    private class FakeTaxRepository : TaxRepositoryPort {
        val rates = mutableMapOf<String, TaxRate>()
        val auditLogs = mutableListOf<AuditLog>()

        override fun getTaxRate(countryCode: String, category: String): TaxRate? {
            return rates["$countryCode:$category"]
        }

        override fun saveAuditLog(log: AuditLog): AuditLog {
            val saved = log.copy(id = (auditLogs.size + 1).toLong())
            auditLogs.add(saved)
            return saved
        }

        override fun updateAuditStatus(requestId: String, status: AuditStatus, errorMessage: String?) {
            val index = auditLogs.indexOfFirst { it.requestId == requestId }
            if (index != -1) {
                auditLogs[index] = auditLogs[index].copy(status = status, errorMessage = errorMessage)
            }
        }
    }

    private class FakeCache : CachePort {
        val cache = mutableMapOf<String, TaxRate>()
        var hitCount = 0
        var missCount = 0

        override fun getTaxRate(countryCode: String, category: String): TaxRate? {
            val rate = cache["$countryCode:$category"]
            if (rate != null) hitCount++ else missCount++
            return rate
        }

        override fun saveTaxRate(rate: TaxRate) {
            cache["${rate.countryCode}:${rate.category}"] = rate
        }

        override fun evictTaxRate(countryCode: String, category: String) {
            cache.remove("$countryCode:$category")
        }
    }

    @Test
    fun testCalculateTax_CacheMissAndHit() {
        val repo = FakeTaxRepository()
        val cache = FakeCache()
        val service = TaxService(repo, cache)

        // Seed Repo
        val rate = TaxRate("DE", "ELECTRONICS", 19.0)
        repo.rates["DE:ELECTRONICS"] = rate

        val request = TaxRequest(100.0, "DE", "ELECTRONICS")

        // 1st request -> Cache Miss, DB Hit
        val result1 = service.calculateTax("req-1", request)
        assertEquals(100.0, result1.amount, 0.0)
        assertEquals(19.0, result1.taxRate, 0.0)
        assertEquals(19.0, result1.taxAmount, 0.0)
        assertEquals(119.0, result1.totalAmount, 0.0)
        assertEquals(1, cache.missCount)
        assertEquals(0, cache.hitCount)

        // Verify cache populated
        assertNotNull(cache.cache["DE:ELECTRONICS"])

        // 2nd request -> Cache Hit
        val result2 = service.calculateTax("req-2", request)
        assertEquals(119.0, result2.totalAmount, 0.0)
        assertEquals(1, cache.missCount)
        assertEquals(1, cache.hitCount) // hitCount incremented
    }

    @Test(expected = DomainException.RateNotFoundException::class)
    fun testCalculateTax_RateNotFound() {
        val repo = FakeTaxRepository()
        val cache = FakeCache()
        val service = TaxService(repo, cache)

        val request = TaxRequest(100.0, "US", "UNKNOWN")
        service.calculateTax("req-1", request) // Should throw RateNotFoundException
    }
}
