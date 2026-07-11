package com.example.challenge.domain.service

import com.example.challenge.domain.model.Currency
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Stretch Goal 2: simple currency conversion for compliance reporting.
 *
 * Rates are hardcoded snapshots (units of currency per 1 USD) — in production
 * this would call an FX provider with a rate locked at transaction time.
 * Conversion A -> B goes through USD: amount / perUsd(A) * perUsd(B).
 */
object CurrencyConverter {

    // Approximate mid-market rates, snapshot mid-2026.
    private val unitsPerUsd: Map<Currency, BigDecimal> = mapOf(
        Currency.USD to BigDecimal("1.0"),
        Currency.EUR to BigDecimal("0.92"),
        Currency.GBP to BigDecimal("0.79"),
        Currency.MXN to BigDecimal("18.50"),
        Currency.BRL to BigDecimal("5.40"),
        Currency.COP to BigDecimal("4100.0"),
        Currency.CLP to BigDecimal("940.0")
    )

    /** 1 unit of [from] expressed in [to], with 6 significant decimal places. */
    fun rate(from: Currency, to: Currency): BigDecimal {
        val fromPerUsd = unitsPerUsd.getValue(from)
        val toPerUsd = unitsPerUsd.getValue(to)
        return toPerUsd.divide(fromPerUsd, MathContext(12)).setScale(6, RoundingMode.HALF_UP)
    }

    /** Converts [amount] from one currency to another, rounded to 2 decimals. */
    fun convert(amount: BigDecimal, from: Currency, to: Currency): BigDecimal {
        if (from == to) return amount.setScale(2, RoundingMode.HALF_UP)
        return amount.multiply(rate(from, to)).setScale(2, RoundingMode.HALF_UP)
    }
}
