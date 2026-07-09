package com.example.challenge.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TaxRate(
    val countryCode: String,
    val category: String,
    val ratePercentage: Double
)
