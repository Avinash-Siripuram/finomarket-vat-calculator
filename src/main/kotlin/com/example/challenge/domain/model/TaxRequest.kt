package com.example.challenge.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TaxRequest(
    val amount: Double,
    val countryCode: String,
    val category: String
)

@Serializable
data class TaxResult(
    val amount: Double,
    val taxRate: Double,
    val taxAmount: Double,
    val totalAmount: Double,
    val countryCode: String,
    val category: String
)
