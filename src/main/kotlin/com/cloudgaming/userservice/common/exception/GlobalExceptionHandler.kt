package com.cloudgaming.userservice.common.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.util.*

/**
 * - UserNotFound → 404
 * - Validation → 400
 * - AccessDenied → 403
 * - InsufficientFunds → 409
 * - IdempotencyConflict → 409
 * - ConcurrentBalanceOperation → 503 с Retry-After
 * - RoleNotFound → 400
 * - KeycloakApi → 503
 * - RuntimeException → 500 (catch-all)
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val logger = LoggerFactory.getLogger(javaClass)

    // 404 Not Found
    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(
        ex: UserNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("User not found: {}", ex.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(
                error = "USER_NOT_FOUND",
                message = ex.message ?: "User not found",
                path = request.requestURI
            )
        )
    }

    // 400 Bad Request: Validation
    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        val fieldErrors = ex.bindingResult.fieldErrors.associate { error ->
            error.field to (error.defaultMessage ?: "invalid")
        }

        logger.warn("Validation failed: {}", fieldErrors)

        val errorResponse = ErrorResponse(
            error = "VALIDATION_ERROR",
            message = "Request validation failed",
            path = (request.getDescription(false).removePrefix("uri=")),
            details = fieldErrors
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    // 400 Bad Request: Role not found
    @ExceptionHandler(RoleNotFoundException::class)
    fun handleRoleNotFound(
        ex: RoleNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Role not found: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                error = "ROLE_NOT_FOUND",
                message = ex.message ?: "Role not found",
                path = request.requestURI
            )
        )
    }

    // 403 Forbidden
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        ex: AccessDeniedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Access denied: {}", ex.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(
                error = "ACCESS_DENIED",
                message = "You don't have permission to access this resource",
                path = request.requestURI
            )
        )
    }

    // 403 Forbidden
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(
        ex: AuthenticationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Authentication failed: {}", ex.message)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(
                error = "UNAUTHORIZED",
                message = "Authentication required or token invalid",
                path = request.requestURI
            )
        )
    }

    // 409 Conflict: Insufficient funds
    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(
        ex: InsufficientFundsException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Insufficient funds for user {}: have={}, requested={}",
            ex.userId, ex.currentBalance, ex.requestedAmount)

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(
                error = "INSUFFICIENT_FUNDS",
                message = ex.message ?: "Insufficient funds",
                path = request.requestURI,
                details = mapOf(
                    "user_id" to ex.userId.toString(),
                    "current_balance" to ex.currentBalance.toString(),
                    "requested_amount" to ex.requestedAmount.toString()
                )
            )
        )
    }

    // 409 Conflict: Idempotency
    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(
        ex: IdempotencyConflictException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Idempotency conflict for key={}: previous={}",
            ex.idempotencyKey, ex.previousTransactionId)

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(
                error = "IDEMPOTENCY_CONFLICT",
                message = ex.message ?: "Idempotency conflict",
                path = request.requestURI,
                details = mapOf(
                    "idempotency_key" to ex.idempotencyKey,
                    "previous_transaction_id" to ex.previousTransactionId.toString()
                )
            )
        )
    }

    // 503 Service Unavailable: Concurrent
    @ExceptionHandler(ConcurrentBalanceOperationException::class)
    fun handleConcurrentBalanceOperation(
        ex: ConcurrentBalanceOperationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Concurrent balance operation for user {}: {}", ex.userId, ex.message)

        val headers = HttpHeaders().apply {
            add("Retry-After", "5")
        }

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .headers(headers)
            .body(
                ErrorResponse(
                    error = "CONCURRENT_OPERATION",
                    message = ex.message ?: "Concurrent operation in progress, retry later",
                    path = request.requestURI,
                    details = mapOf("user_id" to ex.userId.toString())
                )
            )
    }

    // 503 Service Unavailable: Keycloak
    @ExceptionHandler(KeycloakApiException::class)
    fun handleKeycloakApi(
        ex: KeycloakApiException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.error("Keycloak API error: {}", ex.message, ex)

        val headers = HttpHeaders().apply {
            add("Retry-After", "30")
        }

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .headers(headers)
            .body(
                ErrorResponse(
                    error = "KEYCLOAK_UNAVAILABLE",
                    message = "Identity provider is temporarily unavailable",
                    path = request.requestURI
                )
            )
    }

    // 500 Internal Server Error (catch-all)
    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val errorId = UUID.randomUUID()
        logger.error("Unexpected error [{}]: {}", errorId, ex.message, ex)

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                error = "INTERNAL_ERROR",
                message = "An unexpected error occurred. Reference ID: $errorId",
                path = request.requestURI,
                details = mapOf("error_id" to errorId.toString())
            )
        )
    }
}
