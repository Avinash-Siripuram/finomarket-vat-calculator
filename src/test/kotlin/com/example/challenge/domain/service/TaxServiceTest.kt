package com.example.challenge.domain.service

import com.example.challenge.domain.exceptions.DomainException
import com.example.challenge.domain.model.*
import com.example.challenge.domain.ports.egress.CachePort
import com.example.challenge.domain.ports.egress.TaxRepositoryPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    /** Mirrors the V2 migration seed data. */
    private fun seededService(): Pair<TaxService, FakeCache> {
        val repo = FakeTaxRepository()
        val standard = mapOf(
            "MX" to 16.0, "BR" to 17.0, "CO" to 19.0, "CL" to 19.0,
            "UK" to 20.0, "DE" to 19.0, "ES" to 21.0, "FR" to 20.0
        )
        val books = mapOf("UK" to 0.0, "DE" to 7.0, "ES" to 4.0, "FR" to 5.5)
        val food = mapOf("UK" to 0.0, "DE" to 7.0, "ES" to 10.0, "FR" to 5.5)
        standard.forEach { (c, r) ->
            repo.rates["$c:STANDARD"] = TaxRate(c, "STANDARD", r)
            repo.rates["$c:BOOKS"] = TaxRate(c, "BOOKS", books[c] ?: r)
            repo.rates["$c:FOOD"] = TaxRate(c, "FOOD", food[c] ?: r)
        }
        val cache = FakeCache()
        return TaxService(repo, cache) to cache
    }

    private fun request(
        seller: String, buyer: String, product: String, txType: String,
        net: Double, currency: String, category: String? = null, reporting: String? = null
    ) = TaxCalculationRequest(seller, buyer, product, txType, net, currency, category, reporting)

    // -----------------------------------------------------------------------
    // Core calculation
    // -----------------------------------------------------------------------

    @Test
    fun domesticGermanSale_applies19Percent() {
        val (service, _) = seededService()
        val result = service.calculateTax("r1", request("DE", "DE", "PHYSICAL", "B2C", 250.0, "EUR"))
        assertEquals(19.0, result.taxRate, 0.0)
        assertEquals(47.5, result.taxAmount, 0.0)
        assertEquals(297.5, result.totalAmount, 0.0)
        assertEquals("DE", result.taxJurisdiction)
        assertTrue(result.reasoning.contains("Domestic"))
    }

    @Test
    fun latamExport_isZeroRated() {
        val (service, _) = seededService()
        val result = service.calculateTax("r1", request("MX", "BR", "PHYSICAL", "B2C", 400.0, "USD"))
        assertEquals(0.0, result.taxRate, 0.0)
        assertEquals(0.0, result.taxAmount, 0.0)
        assertEquals(400.0, result.totalAmount, 0.0)
        assertEquals("NONE", result.taxJurisdiction)
    }

    @Test
    fun latamToEuDigitalB2C_usesBuyerCountryRate() {
        val (service, _) = seededService()
        val result = service.calculateTax("r1", request("MX", "DE", "DIGITAL", "B2C", 100.0, "USD"))
        assertEquals(19.0, result.taxRate, 0.0)
        assertEquals("DE", result.taxJurisdiction)
    }

    @Test
    fun euToEuB2B_isReverseCharge() {
        val (service, _) = seededService()
        val result = service.calculateTax("r1", request("DE", "FR", "PHYSICAL", "B2B", 1000.0, "EUR"))
        assertEquals(0.0, result.taxAmount, 0.0)
        assertEquals("NONE", result.taxJurisdiction)
        assertTrue(result.reasoning.contains("reverse charge"))
    }

    @Test
    fun euToEuB2C_usesBuyerCountryRate() {
        val (service, _) = seededService()
        val result = service.calculateTax("r1", request("FR", "ES", "PHYSICAL", "B2C", 120.0, "EUR"))
        assertEquals(21.0, result.taxRate, 0.0)
        assertEquals(25.2, result.taxAmount, 0.001)
        assertEquals("ES", result.taxJurisdiction)
    }

    @Test
    fun taxAmount_isRoundedHalfUpToTwoDecimals() {
        val (service, _) = seededService()
        // 33.33 * 19% = 6.3327 -> 6.33 ; total 39.66
        val result = service.calculateTax("r1", request("DE", "DE", "PHYSICAL", "B2C", 33.33, "EUR"))
        assertEquals(6.33, result.taxAmount, 0.0)
        assertEquals(39.66, result.totalAmount, 0.0)
    }

    @Test
    fun countryNamesAndLowercaseCodes_areAccepted() {
        val (service, _) = seededService()
        val result = service.calculateTax("r1", request("mexico", "germany", "digital", "b2c", 100.0, "usd"))
        assertEquals("MX", result.sellerCountry)
        assertEquals("DE", result.taxJurisdiction)
    }

    // -----------------------------------------------------------------------
    // Stretch Goal 1: reduced categories
    // -----------------------------------------------------------------------

    @Test
    fun ukBooks_areZeroRated_butJurisdictionIsStillUk() {
        val (service, _) = seededService()
        val result = service.calculateTax("r1", request("UK", "UK", "PHYSICAL", "B2C", 50.0, "GBP", "BOOKS"))
        assertEquals(0.0, result.taxRate, 0.0)
        assertEquals("UK", result.taxJurisdiction)
        assertEquals("BOOKS", result.productCategory)
    }

    @Test
    fun crossBorderBooks_useDestinationCountryReducedRate() {
        val (service, _) = seededService()
        val result = service.calculateTax("r1", request("FR", "DE", "PHYSICAL", "B2C", 100.0, "EUR", "BOOKS"))
        assertEquals(7.0, result.taxRate, 0.0)
        assertEquals(7.0, result.taxAmount, 0.0)
    }

    @Test
    fun reverseCharge_overridesReducedCategory() {
        val (service, _) = seededService()
        val result = service.calculateTax("r1", request("UK", "DE", "PHYSICAL", "B2B", 100.0, "EUR", "BOOKS"))
        assertEquals(0.0, result.taxRate, 0.0)
        assertEquals("NONE", result.taxJurisdiction)
    }

    // -----------------------------------------------------------------------
    // Stretch Goal 2: reporting currency
    // -----------------------------------------------------------------------

    @Test
    fun reportingCurrency_convertsTaxAmounts() {
        val (service, _) = seededService()
        val result = service.calculateTax("r1", request("DE", "DE", "PHYSICAL", "B2C", 100.0, "EUR", null, "USD"))
        assertNotNull(result.reporting)
        val reporting = result.reporting!!
        assertEquals("USD", reporting.currency)
        // EUR -> USD at 1/0.92 = 1.086957: tax 19.00 EUR -> 20.65 USD
        assertEquals(20.65, reporting.taxAmount, 0.01)
        assertEquals(129.35, reporting.totalAmount, 0.01)
    }

    @Test
    fun reportingCurrency_sameAsTransactionCurrency_isOmitted() {
        val (service, _) = seededService()
        val result = service.calculateTax("r1", request("DE", "DE", "PHYSICAL", "B2C", 100.0, "EUR", null, "EUR"))
        assertEquals(null, result.reporting)
    }

    // -----------------------------------------------------------------------
    // Validation errors
    // -----------------------------------------------------------------------

    @Test(expected = DomainException.InvalidPayloadException::class)
    fun unsupportedCountry_throwsInvalidPayload() {
        val (service, _) = seededService()
        service.calculateTax("r1", request("US", "DE", "PHYSICAL", "B2C", 100.0, "USD"))
    }

    @Test(expected = DomainException.InvalidPayloadException::class)
    fun nonPositiveAmount_throwsInvalidPayload() {
        val (service, _) = seededService()
        service.calculateTax("r1", request("DE", "DE", "PHYSICAL", "B2C", 0.0, "EUR"))
    }

    @Test(expected = DomainException.InvalidPayloadException::class)
    fun unsupportedCurrency_throwsInvalidPayload() {
        val (service, _) = seededService()
        service.calculateTax("r1", request("DE", "DE", "PHYSICAL", "B2C", 100.0, "JPY"))
    }

    // -----------------------------------------------------------------------
    // Cache behaviour (kept from base structure)
    // -----------------------------------------------------------------------

    @Test
    fun rateLookup_usesCacheAside() {
        val (service, cache) = seededService()
        val req = request("DE", "DE", "PHYSICAL", "B2C", 100.0, "EUR")

        service.calculateTax("r1", req)
        assertEquals(1, cache.missCount)
        assertNotNull(cache.cache["DE:STANDARD"])

        service.calculateTax("r2", req)
        assertEquals(1, cache.hitCount)
    }

    // -----------------------------------------------------------------------
    // Core Requirement 2: batch validation
    // -----------------------------------------------------------------------

    @Test
    fun batchValidation_classifiesCorrectOverAndUndercharged() {
        val (service, _) = seededService()
        val batch = BatchValidationRequest(
            transactions = listOf(
                // Correct: DE domestic 100 EUR -> expected 19.00
                HistoricalTransaction("t1", "DE", "DE", "PHYSICAL", "B2C", 100.0, "EUR", null, 19.0),
                // Overcharged: MX -> ES physical export, expected 0, charged 64 (legacy bug: Mexican IVA applied)
                HistoricalTransaction("t2", "MX", "ES", "PHYSICAL", "B2C", 400.0, "USD", null, 64.0),
                // Undercharged: DE domestic 150 EUR -> expected 28.50, charged 0
                HistoricalTransaction("t3", "DE", "DE", "PHYSICAL", "B2C", 150.0, "EUR", null, 0.0),
                // Invalid: unsupported country
                HistoricalTransaction("t4", "US", "DE", "PHYSICAL", "B2C", 100.0, "USD", null, 8.25)
            )
        )

        val result = service.validateBatch("r1", batch)

        assertEquals(4, result.summary.totalTransactions)
        assertEquals(1, result.summary.correctCount)
        assertEquals(1, result.summary.overchargedCount)
        assertEquals(1, result.summary.underchargedCount)
        assertEquals(1, result.summary.invalidCount)

        val byId = result.results.associateBy { it.id }
        assertEquals("CORRECT", byId["t1"]!!.status)
        assertEquals("OVERCHARGED", byId["t2"]!!.status)
        assertEquals(64.0, byId["t2"]!!.discrepancy!!, 0.001)
        assertEquals("UNDERCHARGED", byId["t3"]!!.status)
        assertEquals(-28.5, byId["t3"]!!.discrepancy!!, 0.001)
        assertEquals("INVALID", byId["t4"]!!.status)

        // Discrepancy totals per currency (correct rows excluded)
        assertEquals(64.0, result.summary.netDiscrepancyByCurrency["USD"]!!, 0.001)
        assertEquals(-28.5, result.summary.netDiscrepancyByCurrency["EUR"]!!, 0.001)
        assertEquals(28.5, result.summary.absoluteDiscrepancyByCurrency["EUR"]!!, 0.001)
    }

    @Test
    fun batchValidation_toleratesOneCentRoundingDifference() {
        val (service, _) = seededService()
        // Expected tax on 33.33 EUR at 19% = 6.33; charged 6.34 -> within tolerance
        val batch = BatchValidationRequest(
            transactions = listOf(
                HistoricalTransaction("t1", "DE", "DE", "PHYSICAL", "B2C", 33.33, "EUR", null, 6.34)
            )
        )
        val result = service.validateBatch("r1", batch)
        assertEquals("CORRECT", result.results[0].status)
    }
}
