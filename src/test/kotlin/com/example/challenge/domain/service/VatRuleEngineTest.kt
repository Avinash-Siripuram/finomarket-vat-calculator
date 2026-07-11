package com.example.challenge.domain.service

import com.example.challenge.domain.model.Country
import com.example.challenge.domain.model.ProductType
import com.example.challenge.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VatRuleEngineTest {

    @Test
    fun domesticSale_appliesSellerCountryRate_forAllProductAndTransactionTypes() {
        for (country in Country.entries) {
            for (product in ProductType.entries) {
                for (txType in TransactionType.entries) {
                    val decision = VatRuleEngine.decide(country, country, product, txType)
                    assertEquals("domestic $country $product $txType", country, decision.jurisdiction)
                }
            }
        }
    }

    @Test
    fun latamToLatamCrossBorder_isZeroRatedExport() {
        val decision = VatRuleEngine.decide(Country.MEXICO, Country.BRAZIL, ProductType.PHYSICAL, TransactionType.B2C)
        assertNull(decision.jurisdiction)
        assertTrue(decision.reasoning.contains("import taxes"))
    }

    @Test
    fun latamToLatam_digitalB2C_isStillExport_digitalRuleOnlyAppliesToEuBuyers() {
        val decision = VatRuleEngine.decide(Country.BRAZIL, Country.COLOMBIA, ProductType.DIGITAL, TransactionType.B2C)
        assertNull(decision.jurisdiction)
    }

    @Test
    fun latamToEu_digitalB2C_taxedAtBuyerCountry() {
        val decision = VatRuleEngine.decide(Country.MEXICO, Country.GERMANY, ProductType.DIGITAL, TransactionType.B2C)
        assertEquals(Country.GERMANY, decision.jurisdiction)
        assertTrue(decision.reasoning.contains("destination-based"))
    }

    @Test
    fun latamToEu_digitalB2B_isReverseCharge() {
        val decision = VatRuleEngine.decide(Country.MEXICO, Country.SPAIN, ProductType.DIGITAL, TransactionType.B2B)
        assertNull(decision.jurisdiction)
        assertTrue(decision.reasoning.contains("reverse charge"))
    }

    @Test
    fun latamToEu_physical_isZeroRated_buyerPaysImportVat() {
        for (txType in TransactionType.entries) {
            val decision = VatRuleEngine.decide(Country.CHILE, Country.FRANCE, ProductType.PHYSICAL, txType)
            assertNull("physical $txType", decision.jurisdiction)
            assertTrue(decision.reasoning.contains("import VAT"))
        }
    }

    @Test
    fun latamToEu_services_isZeroRatedExport() {
        val decision = VatRuleEngine.decide(Country.COLOMBIA, Country.GERMANY, ProductType.SERVICES, TransactionType.B2C)
        assertNull(decision.jurisdiction)
    }

    @Test
    fun euToEu_b2c_taxedAtBuyerCountry_forAllProductTypes() {
        for (product in ProductType.entries) {
            val decision = VatRuleEngine.decide(Country.GERMANY, Country.FRANCE, product, TransactionType.B2C)
            assertEquals("EU-EU B2C $product", Country.FRANCE, decision.jurisdiction)
        }
    }

    @Test
    fun euToEu_b2b_isReverseCharge_forAllProductTypes() {
        for (product in ProductType.entries) {
            val decision = VatRuleEngine.decide(Country.SPAIN, Country.UNITED_KINGDOM, product, TransactionType.B2B)
            assertNull("EU-EU B2B $product", decision.jurisdiction)
            assertTrue(decision.reasoning.contains("reverse charge"))
        }
    }

    @Test
    fun euToLatam_isZeroRatedExport_evenForDigitalB2C() {
        val decision = VatRuleEngine.decide(Country.GERMANY, Country.BRAZIL, ProductType.DIGITAL, TransactionType.B2C)
        assertNull(decision.jurisdiction)
    }
}
