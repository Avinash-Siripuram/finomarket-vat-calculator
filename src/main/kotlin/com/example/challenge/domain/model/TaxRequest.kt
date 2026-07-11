package com.example.challenge.domain.model

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Core Requirement 1: Tax Calculation
// ---------------------------------------------------------------------------

/**
 * Incoming transaction for tax calculation.
 * Country/type fields are strings on the wire and validated/parsed by the service
 * so callers get a clear 400 (INVALID_PAYLOAD) instead of a serialization error.
 */
@Serializable
data class TaxCalculationRequest(
    val sellerCountry: String,
    val buyerCountry: String,
    val productType: String,
    val transactionType: String,
    val netAmount: Double,
    val currency: String,
    /** Optional (Stretch Goal 1). Defaults to STANDARD. */
    val productCategory: String? = null,
    /** Optional (Stretch Goal 2). If set, tax amounts are also returned converted to this currency. */
    val reportingCurrency: String? = null
)

@Serializable
data class TaxCalculationResult(
    val sellerCountry: String,
    val buyerCountry: String,
    val productType: String,
    val transactionType: String,
    val productCategory: String,
    val netAmount: Double,
    val currency: String,
    /** Tax rate applied, as a percentage (e.g. 20.0 for 20%). */
    val taxRate: Double,
    /** Tax amount in the transaction currency, rounded to 2 decimals. */
    val taxAmount: Double,
    /** net + tax, in the transaction currency. */
    val totalAmount: Double,
    /** Country code whose tax applies, or "NONE" for zero-rated cross-border cases. */
    val taxJurisdiction: String,
    /** Human-readable explanation of why this rate was applied. */
    val reasoning: String,
    /** Present only when a reportingCurrency was requested (Stretch Goal 2). */
    val reporting: ReportingAmounts? = null
)

/** Tax amounts converted into a compliance/reporting currency (Stretch Goal 2). */
@Serializable
data class ReportingAmounts(
    val currency: String,
    /** Exchange rate used: 1 unit of transaction currency = exchangeRate units of reporting currency. */
    val exchangeRate: Double,
    val netAmount: Double,
    val taxAmount: Double,
    val totalAmount: Double
)

// ---------------------------------------------------------------------------
// Core Requirement 2: Batch Validation (compliance audit)
// ---------------------------------------------------------------------------

@Serializable
data class HistoricalTransaction(
    val id: String,
    val sellerCountry: String,
    val buyerCountry: String,
    val productType: String,
    val transactionType: String,
    val netAmount: Double,
    val currency: String,
    val productCategory: String? = null,
    /** The tax amount the merchant actually charged at the time. */
    val chargedTaxAmount: Double
)

@Serializable
data class BatchValidationRequest(
    val transactions: List<HistoricalTransaction>
)

/** CORRECT | OVERCHARGED | UNDERCHARGED | INVALID (unparseable transaction). */
@Serializable
data class TransactionValidation(
    val id: String,
    val status: String,
    val currency: String,
    val expectedTaxRate: Double? = null,
    val expectedTaxAmount: Double? = null,
    val chargedTaxAmount: Double,
    /** charged - expected. Positive = overcharged, negative = undercharged. */
    val discrepancy: Double? = null,
    val taxJurisdiction: String? = null,
    val reasoning: String
)

@Serializable
data class BatchValidationSummary(
    val totalTransactions: Int,
    val correctCount: Int,
    val overchargedCount: Int,
    val underchargedCount: Int,
    val invalidCount: Int,
    /** Sum of (charged - expected) per currency: net compliance exposure. */
    val netDiscrepancyByCurrency: Map<String, Double>,
    /** Sum of |charged - expected| per currency: total error magnitude. */
    val absoluteDiscrepancyByCurrency: Map<String, Double>
)

@Serializable
data class BatchValidationResult(
    val summary: BatchValidationSummary,
    val results: List<TransactionValidation>
)
