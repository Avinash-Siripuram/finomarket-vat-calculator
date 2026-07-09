package com.example.challenge.domain.ports.egress

import com.example.challenge.domain.model.TaxRate

interface CachePort {
    fun getTaxRate(countryCode: String, category: String): TaxRate?
    fun saveTaxRate(rate: TaxRate)
    fun evictTaxRate(countryCode: String, category: String)
}
