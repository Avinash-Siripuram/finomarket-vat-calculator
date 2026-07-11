package com.example.challenge.adapters.ingress.rest

import com.example.challenge.domain.exceptions.DomainException
import com.example.challenge.domain.model.BatchValidationRequest
import com.example.challenge.domain.model.TaxCalculationRequest
import com.example.challenge.domain.ports.ingress.TaxIngressPort
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.serialization.json.Json
import java.util.UUID

val RequestIdKey = AttributeKey<String>("RequestId")

// Lenient parser: test-data files carry extra documentation fields ("description",
// "expected") that the API should simply ignore.
private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun Route.taxRoutes(taxService: TaxIngressPort) {
    route("/tax") {

        // Core Requirement 1: single-transaction tax calculation
        post("/calculate") {
            val requestId = UUID.randomUUID().toString()
            call.attributes.put(RequestIdKey, requestId)

            // Raw body is persisted first so even malformed payloads are auditable.
            val rawBody = call.receiveText()
            taxService.createAuditLog(requestId, rawBody)

            val request = try {
                json.decodeFromString<TaxCalculationRequest>(rawBody)
            } catch (e: Exception) {
                throw DomainException.InvalidPayloadException(
                    "Invalid JSON format or missing required fields: ${e.message}"
                )
            }

            val result = taxService.calculateTax(requestId, request)

            taxService.finalizeAuditLog(requestId, true)
            call.respond(HttpStatusCode.OK, result)
        }

        // Core Requirement 2: batch validation of historical transactions
        post("/validate-batch") {
            val requestId = UUID.randomUUID().toString()
            call.attributes.put(RequestIdKey, requestId)

            val rawBody = call.receiveText()
            taxService.createAuditLog(requestId, rawBody)

            val request = try {
                json.decodeFromString<BatchValidationRequest>(rawBody)
            } catch (e: Exception) {
                throw DomainException.InvalidPayloadException(
                    "Invalid JSON format or missing required fields: ${e.message}"
                )
            }
            if (request.transactions.isEmpty()) {
                throw DomainException.InvalidPayloadException("transactions list must not be empty")
            }

            val result = taxService.validateBatch(requestId, request)

            taxService.finalizeAuditLog(requestId, true)
            call.respond(HttpStatusCode.OK, result)
        }
    }

    get("/health") {
        call.respond(mapOf("status" to "UP", "message" to "FinoMarket VAT calculation service is running"))
    }
}
