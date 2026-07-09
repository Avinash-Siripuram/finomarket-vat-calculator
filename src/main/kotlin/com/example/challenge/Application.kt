package com.example.challenge

import com.example.challenge.adapters.egress.cache.RedisCacheRepository
import com.example.challenge.adapters.egress.db.PostgresTaxRepository
import com.example.challenge.adapters.ingress.rest.RequestIdKey
import com.example.challenge.adapters.ingress.rest.taxRoutes
import com.example.challenge.config.DatabaseConfig
import com.example.challenge.config.RedisConfig
import com.example.challenge.domain.exceptions.DomainException
import com.example.challenge.domain.ports.ingress.TaxIngressPort
import com.example.challenge.domain.service.TaxService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val logger = LoggerFactory.getLogger("Application")

    // 1. Initialize Configuration & Database/Redis
    DatabaseConfig.init(environment.config)
    RedisConfig.init(environment.config)

    // 2. Manual Dependency Injection (Clean Architecture)
    val pgRepository = PostgresTaxRepository()
    val redisCache = RedisCacheRepository()
    val taxService: TaxIngressPort = TaxService(pgRepository, redisCache)

    // 3. Configure Plugins
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(StatusPages) {
        // Intercept Domain Exceptions
        exception<DomainException> { call, cause ->
            val requestId = call.attributes.getOrNull(RequestIdKey)
            logger.error("[Request: {}] Domain exception caught: {}", requestId ?: "UNKNOWN", cause.message)

            // Finalize audit log to FAILED
            if (requestId != null) {
                try {
                    taxService.finalizeAuditLog(requestId, false, cause.message)
                } catch (e: Exception) {
                    logger.error("Failed to finalize audit log in exception handler: {}", e.message)
                }
            }

            call.respond(
                cause.statusCode,
                ErrorResponse(
                    success = false,
                    errorCode = cause.errorCode,
                    message = cause.message
                )
            )
        }

        // Intercept all other system failures
        exception<Throwable> { call, cause ->
            val requestId = call.attributes.getOrNull(RequestIdKey)
            logger.error("[Request: {}] Unexpected server error caught", requestId ?: "UNKNOWN", cause)

            // Finalize audit log to FAILED
            if (requestId != null) {
                try {
                    taxService.finalizeAuditLog(requestId, false, cause.message ?: "Unknown internal server error")
                } catch (e: Exception) {
                    logger.error("Failed to finalize audit log in exception handler: {}", e.message)
                }
            }

            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    success = false,
                    errorCode = "INTERNAL_SERVER_ERROR",
                    message = cause.message ?: "An unexpected error occurred"
                )
            )
        }
    }

    // 4. Setup Routes
    routing {
        taxRoutes(taxService)
    }

    // 5. Shutdown hooks
    monitor.subscribe(ApplicationStopped) {
        logger.info("Application stopping, cleaning up resources...")
        DatabaseConfig.close()
        RedisConfig.close()
    }
}

@Serializable
data class ErrorResponse(
    val success: Boolean,
    val errorCode: String,
    val message: String
)
