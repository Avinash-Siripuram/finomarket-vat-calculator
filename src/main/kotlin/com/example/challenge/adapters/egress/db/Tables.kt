package com.example.challenge.adapters.egress.db

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object TaxRatesTable : Table("tax_rates") {
    val countryCode = varchar("country_code", 10)
    val category = varchar("category", 50)
    val ratePercentage = double("rate_percentage")
    
    override val primaryKey = PrimaryKey(countryCode, category)
}

object AuditLogsTable : LongIdTable("audit_logs") {
    val requestId = varchar("request_id", 100).uniqueIndex()
    val payload = text("payload")
    val status = varchar("status", 20)
    val errorMessage = text("error_message").nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}
