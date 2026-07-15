package com.cloudgaming.userservice.common.exception

import com.cloudgaming.userservice.constants.ErrorCode
import com.cloudgaming.userservice.constants.ErrorDetailKey
import com.cloudgaming.userservice.constants.RetryAfterSeconds
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponseException
import java.util.*

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(
        ex: UserNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("User not found: {}", ex.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(
                error = ErrorCode.USER_NOT_FOUND.code,
                message = ex.message ?: ErrorCode.USER_NOT_FOUND.defaultMessage,
                path = request.requestURI
            )
        )
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        val fieldErrors = ex.bindingResult.fieldErrors
            .groupBy({ it.field }, { it.defaultMessage ?: "invalid" })

        logger.warn("Validation failed: {}", fieldErrors)

        val errorResponse = ErrorResponse(
            error = ErrorCode.VALIDATION_ERROR.code,
            message = ErrorCode.VALIDATION_ERROR.defaultMessage,
            path = (request.getDescription(false).removePrefix("uri=")),
            details = fieldErrors
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    override fun handleHttpRequestMethodNotSupported(
        ex: HttpRequestMethodNotSupportedException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        return createErrorResponse(
            error = ErrorCode.METHOD_NOT_ALLOWED.code,
            message = "Request method '${ex.method}' not supported",
            status = HttpStatus.METHOD_NOT_ALLOWED,
            request = request,
            headers = headers
        )
    }

    override fun handleHttpMediaTypeNotSupported(
        ex: HttpMediaTypeNotSupportedException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        return createErrorResponse(
            error = ErrorCode.UNSUPPORTED_MEDIA_TYPE.code,
            message = "Content type '${ex.contentType}' not supported",
            status = HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            request = request,
            headers = headers
        )
    }

    override fun handleMissingServletRequestParameter(
        ex: MissingServletRequestParameterException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        return createErrorResponse(
            error = ErrorCode.MISSING_PARAMETER.code,
            message = "Missing required parameter: ${ex.parameterName}",
            status = HttpStatus.BAD_REQUEST,
            request = request
        )
    }

    override fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        logger.warn("Malformed JSON request: {}", ex.message)
        return createErrorResponse(
            error = ErrorCode.MALFORMED_JSON.code,
            message = ErrorCode.MALFORMED_JSON.defaultMessage,
            status = HttpStatus.BAD_REQUEST,
            request = request
        )
    }

    override fun handleNoHandlerFoundException(
        ex: NoHandlerFoundException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(
                error = ErrorCode.NOT_FOUND.code,
                message = "No endpoint found for ${ex.httpMethod} ${ex.requestURL}",
                path = request.getDescription(false).removePrefix("uri=")
            )
        )
    }

    override fun handleNoResourceFoundException(
        ex: NoResourceFoundException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        logger.warn("No resource found: {}", ex.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(
                error = ErrorCode.NOT_FOUND.code,
                message = "No endpoint found for ${request.getDescription(false).removePrefix("uri=")}",
                path = request.getDescription(false).removePrefix("uri=")
            )
        )
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any> {
        if (ex is ErrorResponseException) {
            return ResponseEntity.status(status).headers(headers).body(
                ErrorResponse(
                    error = status.toString(),
                    message = ex.message ?: "Error",
                    path = request.getDescription(false).removePrefix("uri=")
                )
            )
        }

        if (body is ErrorResponse) {
            return ResponseEntity.status(status).headers(headers).body(body)
        }

        val message = when (body) {
            is String -> body
            else -> if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
                "${ErrorCode.INTERNAL_ERROR.defaultMessage}. Reference ID: ${UUID.randomUUID()}"
            } else {
                ex.message ?: "Unexpected error"
            }
        }

        val errorResponse = ErrorResponse(
            error = if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
                ErrorCode.INTERNAL_ERROR.code
            } else {
                status.toString()
            },
            message = message,
            path = request.getDescription(false).removePrefix("uri="),
            details = if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
                mapOf(ErrorDetailKey.ERROR_ID to UUID.randomUUID().toString())
            } else {
                null
            }
        )

        return ResponseEntity.status(status).headers(headers).body(errorResponse)
    }

    private fun createErrorResponse(
        error: String,
        message: String,
        status: HttpStatus,
        request: WebRequest,
        headers: HttpHeaders? = null
    ): ResponseEntity<Any>? {
        val errorResponse = ErrorResponse(
            error = error,
            message = message,
            path = (request.getDescription(false).removePrefix("uri="))
        )
        return ResponseEntity.status(status).headers(headers ?: HttpHeaders()).body(errorResponse)
    }

    @ExceptionHandler(RoleNotFoundException::class)
    fun handleRoleNotFound(
        ex: RoleNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Role not found: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                error = ErrorCode.ROLE_NOT_FOUND.code,
                message = ex.message ?: ErrorCode.ROLE_NOT_FOUND.defaultMessage,
                path = request.requestURI
            )
        )
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        ex: AccessDeniedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Access denied: {}", ex.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(
                error = ErrorCode.ACCESS_DENIED.code,
                message = ErrorCode.ACCESS_DENIED.defaultMessage,
                path = request.requestURI
            )
        )
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(
        ex: AuthenticationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Authentication failed: {}", ex.message)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(
                error = ErrorCode.UNAUTHORIZED.code,
                message = ErrorCode.UNAUTHORIZED.defaultMessage,
                path = request.requestURI
            )
        )
    }

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(
        ex: InsufficientFundsException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Insufficient funds for user {}: have={}, requested={}",
            ex.userId, ex.currentBalance, ex.requestedAmount)

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(
                error = ErrorCode.INSUFFICIENT_FUNDS.code,
                message = ex.message ?: ErrorCode.INSUFFICIENT_FUNDS.defaultMessage,
                path = request.requestURI,
                details = mapOf(
                    ErrorDetailKey.USER_ID to ex.userId.toString(),
                    ErrorDetailKey.CURRENT_BALANCE to ex.currentBalance.toString(),
                    ErrorDetailKey.REQUESTED_AMOUNT to ex.requestedAmount.toString()
                )
            )
        )
    }

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(
        ex: IdempotencyConflictException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Idempotency conflict for key={}: previous={}",
            ex.idempotencyKey, ex.previousTransactionId)

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(
                error = ErrorCode.IDEMPOTENCY_CONFLICT.code,
                message = ex.message ?: ErrorCode.IDEMPOTENCY_CONFLICT.defaultMessage,
                path = request.requestURI,
                details = mapOf(
                    ErrorDetailKey.IDEMPOTENCY_KEY to ex.idempotencyKey,
                    ErrorDetailKey.PREVIOUS_TRANSACTION_ID to ex.previousTransactionId.toString()
                )
            )
        )
    }

    @ExceptionHandler(ConcurrentBalanceOperationException::class)
    fun handleConcurrentBalanceOperation(
        ex: ConcurrentBalanceOperationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Concurrent balance operation for user {}: {}", ex.userId, ex.message)

        val headers = HttpHeaders().apply {
            add(HttpHeaders.RETRY_AFTER, RetryAfterSeconds.CONCURRENT_OPERATION)
        }

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .headers(headers)
            .body(
                ErrorResponse(
                    error = ErrorCode.CONCURRENT_OPERATION.code,
                    message = ex.message ?: ErrorCode.CONCURRENT_OPERATION.defaultMessage,
                    path = request.requestURI,
                    details = mapOf(ErrorDetailKey.USER_ID to ex.userId.toString())
                )
            )
    }

    @ExceptionHandler(KeycloakApiException::class)
    fun handleKeycloakApi(
        ex: KeycloakApiException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.error("Keycloak API error: {}", ex.message, ex)

        val headers = HttpHeaders().apply {
            add(HttpHeaders.RETRY_AFTER, RetryAfterSeconds.KEYCLOAK_UNAVAILABLE)
        }

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .headers(headers)
            .body(
                ErrorResponse(
                    error = ErrorCode.KEYCLOAK_UNAVAILABLE.code,
                    message = ErrorCode.KEYCLOAK_UNAVAILABLE.defaultMessage,
                    path = request.requestURI
                )
            )
    }

    }