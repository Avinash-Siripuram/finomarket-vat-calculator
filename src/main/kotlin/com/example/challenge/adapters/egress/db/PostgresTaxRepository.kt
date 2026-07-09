package com.example.challenge.adapters.egress.db

import com.example.challenge.domain.model.AuditLog
import com.example.challenge.domain.model.AuditStatus
import com.example.challenge.domain.model.TaxRate
import com.example.challenge.domain.ports.egress.TaxRepositoryPort
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class PostgresTaxRepository : TaxRepositoryPort {

    override fun getTaxRate(countryCode: String, category: String): TaxRate? {
        return transaction {
            TaxRatesTable.select { 
                (TaxRatesTable.countryCode eq countryCode) and (TaxRatesTable.category eq category) 
            }.map {
                TaxRate(
                    countryCode = it[TaxRatesTable.countryCode],
                    category = it[TaxRatesTable.category],
                    ratePercentage = it[TaxRatesTable.ratePercentage]
                )
            }.singleOrNull()
        }
    }

    override fun saveAuditLog(log: AuditLog): AuditLog {
        return transaction {
            val generatedId = AuditLogsTable.insertAndGetId {
                it[requestId] = log.requestId
                it[payload] = log.payload
                it[status] = log.status.name
                it[errorMessage] = log.errorMessage
                it[createdAt] = log.createdAt
                it[updatedAt] = log.updatedAt
            }
            log.copy(id = generatedId.value)
        }
    }

    override fun updateAuditStatus(requestId: String, status: AuditStatus, errorMessage: String?) {
        transaction {
            AuditLogsTable.update({ AuditLogsTable.requestId eq requestId }) {
                it[AuditLogsTable.status] = status.name
                it[AuditLogsTable.errorMessage] = errorMessage
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
}
