package com.example.challenge.domain.exceptions

import io.ktor.http.*

sealed class DomainException(
    val statusCode: HttpStatusCode,
    override val message: String,
    val errorCode: String
) : RuntimeException(message) {

    class InvalidPayloadException(message: String) : DomainException(
        statusCode = HttpStatusCode.BadRequest,
        message = message,
        errorCode = "INVALID_PAYLOAD"
    )

    class RateNotFoundException(countryCode: String, category: String) : DomainException(
        statusCode = HttpStatusCode.NotFound,
        message = "Tax rate not found for country: $countryCode, category: $category",
        errorCode = "RATE_NOT_FOUND"
    )

    class SystemFailureException(message: String, cause: Throwable? = null) : DomainException(
        statusCode = HttpStatusCode.InternalServerError,
        message = message,
        errorCode = "SYSTEM_FAILURE"
    )
}
