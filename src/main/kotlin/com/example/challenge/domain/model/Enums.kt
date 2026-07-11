package com.example.challenge.domain.model

/**
 * Supported regions.
 *
 * NOTE: The UK is treated as part of the "EU" region for this prototype, per the
 * challenge spec ("buyers across Europe: UK, Germany, Spain, France" and the
 * EU-to-EU rules include the UK). Post-Brexit reality is more nuanced.
 */
enum class Region { LATAM, EU }

/**
 * Supported countries. Requests may use the ISO-like code ("MX", "UK"),
 * the display name ("Mexico"), or the enum name ("MEXICO") — all case-insensitive.
 */
enum class Country(val code: String, val region: Region, val displayName: String) {
    MEXICO("MX", Region.LATAM, "Mexico"),
    BRAZIL("BR", Region.LATAM, "Brazil"),
    COLOMBIA("CO", Region.LATAM, "Colombia"),
    CHILE("CL", Region.LATAM, "Chile"),
    UNITED_KINGDOM("UK", Region.EU, "United Kingdom"),
    GERMANY("DE", Region.EU, "Germany"),
    SPAIN("ES", Region.EU, "Spain"),
    FRANCE("FR", Region.EU, "France");

    companion object {
        fun parse(value: String): Country? {
            val v = value.trim()
            if (v.equals("GB", ignoreCase = true)) return UNITED_KINGDOM
            return entries.firstOrNull {
                it.code.equals(v, ignoreCase = true) ||
                    it.displayName.equals(v, ignoreCase = true) ||
                    it.name.equals(v.replace(' ', '_'), ignoreCase = true)
            }
        }
    }
}

enum class ProductType {
    PHYSICAL, DIGITAL, SERVICES;

    companion object {
        fun parse(value: String): ProductType? {
            val v = value.trim().uppercase()
            return when (v) {
                "PHYSICAL", "PHYSICAL_GOODS", "PHYSICAL GOODS" -> PHYSICAL
                "DIGITAL", "DIGITAL_GOODS", "DIGITAL GOODS" -> DIGITAL
                "SERVICES", "SERVICE" -> SERVICES
                else -> null
            }
        }
    }
}

enum class TransactionType {
    B2C, B2B;

    companion object {
        fun parse(value: String): TransactionType? =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }
}

/**
 * Product categories for reduced VAT rates (Stretch Goal 1).
 * STANDARD is the default when the request omits the field.
 * Reduced rates only exist for EU countries; LATAM books/food are seeded
 * at the standard rate (documented simplification).
 */
enum class ProductCategory {
    STANDARD, BOOKS, FOOD;

    companion object {
        fun parse(value: String?): ProductCategory? {
            if (value == null || value.isBlank()) return STANDARD
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
        }
    }
}

enum class Currency {
    USD, EUR, GBP, MXN, BRL, COP, CLP;

    companion object {
        fun parse(value: String): Currency? =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }
}
