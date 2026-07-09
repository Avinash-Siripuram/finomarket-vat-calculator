package com.example.challenge.adapters.ingress.rest

import com.example.challenge.domain.exceptions.DomainException
import com.example.challenge.domain.model.TaxRequest
import com.example.challenge.domain.ports.ingress.TaxIngressPort
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.util.UUID

val RequestIdKey = AttributeKey<String>("RequestId")

fun Route.taxRoutes(taxService: TaxIngressPort) {
    route("/tax") {
        
        post("/calculate") {
            val requestId = UUID.randomUUID().toString()
            call.attributes.put(RequestIdKey, requestId)

            // 1. Extract raw body for audit logging
            val rawBody = call.receiveText()
            taxService.createAuditLog(requestId, rawBody)

            // 2. Schema Validation (Explicit payload shape verification)
            val request = try {
                kotlinx.serialization.json.Json.decodeFromString<TaxRequest>(rawBody)
            } catch (e: Exception) {
                throw DomainException.InvalidPayloadException("Invalid JSON format or missing required fields: ${e.message}")
            }

            // Explicit property validations
            validatePayload(request)

            // 3. Process
            val result = taxService.calculateTax(requestId, request)

            // 4. Finalize Audit status to SUCCESS
            taxService.finalizeAuditLog(requestId, true)

            call.respond(HttpStatusCode.OK, result)
        }
    }

    get("/health") {
        call.respond(mapOf("status" to "UP", "message" to "Ktor tax service is running"))
    }
}

private fun validatePayload(request: TaxRequest) {
    if (request.amount <= 0) {
        throw DomainException.InvalidPayloadException("Amount must be greater than zero")
    }
    if (request.countryCode.isBlank()) {
        throw DomainException.InvalidPayloadException("Country code is mandatory")
    }
    if (request.category.isBlank()) {
        throw DomainException.InvalidPayloadException("Category is mandatory")
    }
}
