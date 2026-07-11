package com.example.challenge.domain.service

import com.example.challenge.domain.exceptions.DomainException
import com.example.challenge.domain.model.*
import com.example.challenge.domain.ports.egress.CachePort
import com.example.challenge.domain.ports.egress.TaxRepositoryPort
import com.example.challenge.domain.ports.ingress.TaxIngressPort
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode

class TaxService(
    private val repositoryPort: TaxRepositoryPort,
    private val cachePort: CachePort
) : TaxIngressPort {

    private val logger = LoggerFactory.getLogger(TaxService::class.java)

    companion object {
        /** Charged-vs-expected differences within 1 cent are treated as rounding noise. */
        private val DISCREPANCY_TOLERANCE = BigDecimal("0.01")
    }

    // -----------------------------------------------------------------------
    // Core Requirement 1: tax calculation
    // -----------------------------------------------------------------------

    override fun calculateTax(requestId: String, request: TaxCalculationRequest): TaxCalculationResult {
        logger.info(
            "[Request: {}] Calculating tax: {} -> {}, {}/{}, {} {}",
            requestId, request.sellerCountry, request.buyerCountry,
            request.productType, request.transactionType, request.netAmount, request.currency
        )

        val parsed = parse(request)
        val decision = VatRuleEngine.decide(parsed.seller, parsed.buyer, parsed.productType, parsed.transactionType)

        val (rate, reasoning) = resolveRate(decision, parsed.category)

        val netAmount = BigDecimal(request.netAmount.toString())
        val taxAmount = netAmount
            .multiply(rate)
            .divide(BigDecimal(100))
            .setScale(2, RoundingMode.HALF_UP)
        val totalAmount = netAmount.setScale(2, RoundingMode.HALF_UP).add(taxAmount)

        val reporting = parsed.reportingCurrency
            ?.takeIf { it != parsed.currency }
            ?.let { reportingCurrency ->
                ReportingAmounts(
                    currency = reportingCurrency.name,
                    exchangeRate = CurrencyConverter.rate(parsed.currency, reportingCurrency).toDouble(),
                    netAmount = CurrencyConverter.convert(netAmount, parsed.currency, reportingCurrency).toDouble(),
                    taxAmount = CurrencyConverter.convert(taxAmount, parsed.currency, reportingCurrency).toDouble(),
                    totalAmount = CurrencyConverter.convert(totalAmount, parsed.currency, reportingCurrency).toDouble()
                )
            }

        return TaxCalculationResult(
            sellerCountry = parsed.seller.code,
            buyerCountry = parsed.buyer.code,
            productType = parsed.productType.name,
            transactionType = parsed.transactionType.name,
            productCategory = parsed.category.name,
            netAmount = netAmount.setScale(2, RoundingMode.HALF_UP).toDouble(),
            currency = parsed.currency.name,
            taxRate = rate.toDouble(),
            taxAmount = taxAmount.toDouble(),
            totalAmount = totalAmount.toDouble(),
            taxJurisdiction = decision.jurisdiction?.code ?: "NONE",
            reasoning = reasoning,
            reporting = reporting
        )
    }

    // -----------------------------------------------------------------------
    // Core Requirement 2: batch validation of historical transactions
    // -----------------------------------------------------------------------

    override fun validateBatch(requestId: String, request: BatchValidationRequest): BatchValidationResult {
        logger.info("[Request: {}] Validating batch of {} historical transactions", requestId, request.transactions.size)

        val results = request.transactions.map { txn -> validateTransaction(requestId, txn) }

        val netByCurrency = mutableMapOf<String, BigDecimal>()
        val absByCurrency = mutableMapOf<String, BigDecimal>()
        results.filter { it.discrepancy != null && it.status != "CORRECT" }.forEach { r ->
            val d = BigDecimal(r.discrepancy.toString())
            netByCurrency.merge(r.currency, d, BigDecimal::add)
            absByCurrency.merge(r.currency, d.abs(), BigDecimal::add)
        }

        val summary = BatchValidationSummary(
            totalTransactions = results.size,
            correctCount = results.count { it.status == "CORRECT" },
            overchargedCount = results.count { it.status == "OVERCHARGED" },
            underchargedCount = results.count { it.status == "UNDERCHARGED" },
            invalidCount = results.count { it.status == "INVALID" },
            netDiscrepancyByCurrency = netByCurrency.mapValues { it.value.setScale(2, RoundingMode.HALF_UP).toDouble() },
            absoluteDiscrepancyByCurrency = absByCurrency.mapValues { it.value.setScale(2, RoundingMode.HALF_UP).toDouble() }
        )

        return BatchValidationResult(summary = summary, results = results)
    }

    /** One bad row must not fail the whole audit — it is reported as INVALID instead. */
    private fun validateTransaction(requestId: String, txn: HistoricalTransaction): TransactionValidation {
        val expected = try {
            calculateTax(
                requestId,
                TaxCalculationRequest(
                    sellerCountry = txn.sellerCountry,
                    buyerCountry = txn.buyerCountry,
                    productType = txn.productType,
                    transactionType = txn.transactionType,
                    netAmount = txn.netAmount,
                    currency = txn.currency,
                    productCategory = txn.productCategory
                )
            )
        } catch (e: DomainException) {
            return TransactionValidation(
                id = txn.id,
                status = "INVALID",
                currency = txn.currency,
                chargedTaxAmount = txn.chargedTaxAmount,
                reasoning = "Could not validate: ${e.message}"
            )
        }

        val charged = BigDecimal(txn.chargedTaxAmount.toString()).setScale(2, RoundingMode.HALF_UP)
        val expectedTax = BigDecimal(expected.taxAmount.toString())
        val discrepancy = charged.subtract(expectedTax)

        val status = when {
            discrepancy.abs() <= DISCREPANCY_TOLERANCE -> "CORRECT"
            discrepancy.signum() > 0 -> "OVERCHARGED"
            else -> "UNDERCHARGED"
        }

        return TransactionValidation(
            id = txn.id,
            status = status,
            currency = expected.currency,
            expectedTaxRate = expected.taxRate,
            expectedTaxAmount = expected.taxAmount,
            chargedTaxAmount = charged.toDouble(),
            discrepancy = discrepancy.toDouble(),
            taxJurisdiction = expected.taxJurisdiction,
            reasoning = expected.reasoning
        )
    }

    // -----------------------------------------------------------------------
    // Audit log lifecycle (unchanged from base structure)
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private data class ParsedRequest(
        val seller: Country,
        val buyer: Country,
        val productType: ProductType,
        val transactionType: TransactionType,
        val category: ProductCategory,
        val currency: Currency,
        val reportingCurrency: Currency?
    )

    private fun parse(request: TaxCalculationRequest): ParsedRequest {
        if (request.netAmount <= 0) {
            throw DomainException.InvalidPayloadException("netAmount must be greater than zero")
        }
        val seller = Country.parse(request.sellerCountry)
            ?: throw DomainException.InvalidPayloadException(
                "Unsupported sellerCountry '${request.sellerCountry}'. Supported: ${supportedCountries()}"
            )
        val buyer = Country.parse(request.buyerCountry)
            ?: throw DomainException.InvalidPayloadException(
                "Unsupported buyerCountry '${request.buyerCountry}'. Supported: ${supportedCountries()}"
            )
        val productType = ProductType.parse(request.productType)
            ?: throw DomainException.InvalidPayloadException(
                "Unsupported productType '${request.productType}'. Supported: PHYSICAL, DIGITAL, SERVICES"
            )
        val transactionType = TransactionType.parse(request.transactionType)
            ?: throw DomainException.InvalidPayloadException(
                "Unsupported transactionType '${request.transactionType}'. Supported: B2C, B2B"
            )
        val category = ProductCategory.parse(request.productCategory)
            ?: throw DomainException.InvalidPayloadException(
                "Unsupported productCategory '${request.productCategory}'. Supported: STANDARD, BOOKS, FOOD"
            )
        val currency = Currency.parse(request.currency)
            ?: throw DomainException.InvalidPayloadException(
                "Unsupported currency '${request.currency}'. Supported: ${Currency.entries.joinToString(", ")}"
            )
        val reportingCurrency = request.reportingCurrency?.takeIf { it.isNotBlank() }?.let {
            Currency.parse(it) ?: throw DomainException.InvalidPayloadException(
                "Unsupported reportingCurrency '$it'. Supported: ${Currency.entries.joinToString(", ")}"
            )
        }
        return ParsedRequest(seller, buyer, productType, transactionType, category, currency, reportingCurrency)
    }

    /**
     * Turns a rule decision into a concrete percentage. Zero-rated decisions need
     * no lookup; otherwise the (jurisdiction, category) rate comes from the DB
     * through the cache-aside path.
     */
    private fun resolveRate(decision: TaxDecision, category: ProductCategory): Pair<BigDecimal, String> {
        val jurisdiction = decision.jurisdiction
            ?: return BigDecimal.ZERO to decision.reasoning

        val taxRate = getTaxRateWithCacheAside(jurisdiction.code, category.name)
        val rate = BigDecimal(taxRate.ratePercentage.toString())

        val reasoning = if (category != ProductCategory.STANDARD) {
            decision.reasoning + " ${category.name} category uses the reduced rate of ${taxRate.ratePercentage}% " +
                "in ${jurisdiction.displayName}."
        } else {
            decision.reasoning
        }
        return rate to reasoning
    }

    private fun supportedCountries(): String =
        Country.entries.joinToString(", ") { "${it.code} (${it.displayName})" }

    private fun getTaxRateWithCacheAside(countryCode: String, category: String): TaxRate {
        // 1. Try cache
        try {
            val cachedRate = cachePort.getTaxRate(countryCode, category)
            if (cachedRate != null) {
                logger.debug("Cache HIT for tax rate: country={}, category={}", countryCode, category)
                return cachedRate
            }
        } catch (e: Exception) {
            logger.warn("Cache lookup failed (Redis connection issue?): {}", e.message)
        }

        // 2. Cache miss -> Database
        logger.debug("Cache MISS for tax rate: country={}, category={}. Querying database.", countryCode, category)
        val dbRate = repositoryPort.getTaxRate(countryCode, category)
            ?: throw DomainException.RateNotFoundException(countryCode, category)

        // 3. Populate cache safely
        try {
            cachePort.saveTaxRate(dbRate)
        } catch (e: Exception) {
            logger.warn("Failed to update cache: {}", e.message)
        }

        return dbRate
    }
}
