package com.example.challenge.domain.service

import com.example.challenge.domain.model.Country
import com.example.challenge.domain.model.ProductType
import com.example.challenge.domain.model.Region
import com.example.challenge.domain.model.TransactionType

/**
 * Outcome of jurisdiction determination.
 *
 * @param jurisdiction the country whose VAT/IVA applies, or null when the
 *        transaction is zero-rated (export / reverse charge / import-VAT cases) —
 *        in that case no rate lookup is needed and the rate is 0%.
 * @param reasoning human-readable explanation of the rule that fired.
 */
data class TaxDecision(
    val jurisdiction: Country?,
    val reasoning: String
)

/**
 * Pure, stateless implementation of the simplified cross-border VAT rules
 * from the FinoMarket challenge spec. Rules are evaluated in order of
 * specificity:
 *
 *  1. Same country               -> seller country rate (domestic sale)
 *  2. LATAM -> LATAM (different) -> 0% export
 *  3. LATAM -> EU:
 *       digital + B2C            -> buyer country rate (destination-based digital rule)
 *       digital + B2B            -> 0% reverse charge
 *       physical                 -> 0% (buyer pays import VAT separately)
 *       services                 -> 0% export
 *  4. EU -> EU (different):
 *       B2B                      -> 0% intra-EU reverse charge
 *       B2C                      -> buyer country rate (destination-based)
 *  5. EU -> LATAM                -> 0% export
 *
 * Simplifications (documented in README): UK treated as EU; seller assumed
 * VAT-registered everywhere; distance-selling thresholds ignored.
 */
object VatRuleEngine {

    fun decide(
        seller: Country,
        buyer: Country,
        productType: ProductType,
        transactionType: TransactionType
    ): TaxDecision {

        // Rule 1: domestic sale — seller's (== buyer's) country rate applies,
        // for both B2C and B2B (domestic B2B is still charged VAT).
        if (seller == buyer) {
            val taxName = if (seller.region == Region.LATAM) "IVA" else "VAT"
            return TaxDecision(
                jurisdiction = seller,
                reasoning = "Domestic sale in ${seller.displayName}: ${seller.displayName} $taxName applies " +
                    "(seller and buyer in the same country)."
            )
        }

        val sellerLatam = seller.region == Region.LATAM
        val buyerLatam = buyer.region == Region.LATAM
        val sellerEu = seller.region == Region.EU
        val buyerEu = buyer.region == Region.EU

        // Rule 2: LATAM -> LATAM cross-border = export, buyer handles import taxes.
        if (sellerLatam && buyerLatam) {
            return TaxDecision(
                jurisdiction = null,
                reasoning = "Cross-border LATAM export (${seller.displayName} -> ${buyer.displayName}): " +
                    "0% — the buyer handles import taxes in ${buyer.displayName}."
            )
        }

        // Rule 3: LATAM -> EU.
        if (sellerLatam && buyerEu) {
            return when {
                // Digital B2C to an EU buyer: destination-based taxation always applies,
                // overriding the general "LATAM -> EU = export" rule.
                productType == ProductType.DIGITAL && transactionType == TransactionType.B2C ->
                    TaxDecision(
                        jurisdiction = buyer,
                        reasoning = "${buyer.displayName} VAT applies: B2C digital goods sold to an EU buyer " +
                            "are always taxed at the buyer's country rate (destination-based taxation), " +
                            "even for a non-EU seller."
                    )

                productType == ProductType.DIGITAL ->
                    TaxDecision(
                        jurisdiction = null,
                        reasoning = "B2B digital sale from ${seller.displayName} to EU buyer in ${buyer.displayName}: " +
                            "0% — reverse charge, the business buyer self-accounts for VAT."
                    )

                productType == ProductType.PHYSICAL ->
                    TaxDecision(
                        jurisdiction = null,
                        reasoning = "Physical goods exported from ${seller.displayName} to ${buyer.displayName}: " +
                            "0% — the buyer pays import VAT separately at customs."
                    )

                else -> // SERVICES
                    TaxDecision(
                        jurisdiction = null,
                        reasoning = "Services exported from ${seller.displayName} (LATAM) to ${buyer.displayName} (EU): " +
                            "0% export."
                    )
            }
        }

        // Rule 4: EU -> EU cross-border.
        if (sellerEu && buyerEu) {
            return if (transactionType == TransactionType.B2B) {
                TaxDecision(
                    jurisdiction = null,
                    reasoning = "Intra-EU B2B sale (${seller.displayName} -> ${buyer.displayName}): " +
                        "0% — reverse charge, the buyer self-accounts for VAT " +
                        "(seller must validate the buyer's VAT number)."
                )
            } else {
                TaxDecision(
                    jurisdiction = buyer,
                    reasoning = "${buyer.displayName} VAT applies: intra-EU B2C sale is taxed at the " +
                        "buyer's country rate (destination-based taxation)."
                )
            }
        }

        // Rule 5: EU -> LATAM = export.
        return TaxDecision(
            jurisdiction = null,
            reasoning = "Export from ${seller.displayName} (EU) to ${buyer.displayName} (LATAM): " +
                "0% — the buyer handles import taxes locally."
        )
    }
}
