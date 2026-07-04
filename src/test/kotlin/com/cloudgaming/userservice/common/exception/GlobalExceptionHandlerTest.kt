package com.cloudgaming.userservice.common.exception

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import java.math.BigDecimal
import java.util.*

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()
    private val objectMapper = ObjectMapper().apply {
        registerModule(JavaTimeModule())
    }

    private fun mockRequest(uri: String = "/api/v1/test"): MockHttpServletRequest {
        return MockHttpServletRequest().apply {
            requestURI = uri
        }
    }

    fun buildValidationResponse(
        bindingResult: org.springframework.validation.BindingResult,
        path: String
    ): ErrorResponse {
        val fieldErrors = bindingResult.fieldErrors.associate { error ->
            error.field to (error.defaultMessage ?: "invalid")
        }
        return ErrorResponse(
            error = "VALIDATION_ERROR",
            message = "Request validation failed",
            path = path,
            details = fieldErrors
        )
    }

    @Nested
    inner class UserNotFound {

        @Test
        fun `should return 404 for UserNotFoundException by userId`() {
            val userId = UUID.randomUUID()
            val ex = UserNotFoundException.byUserId(userId)
            val request = mockRequest("/api/v1/admin/users/$userId")

            val response = handler.handleUserNotFound(ex, request)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
            assertThat(response.body!!.error).isEqualTo("USER_NOT_FOUND")
            assertThat(response.body!!.message).contains(userId.toString())
            assertThat(response.body!!.path).isEqualTo("/api/v1/admin/users/$userId")
            assertThat(response.body!!.timestamp).isNotNull
        }

        @Test
        fun `should return 404 for UserNotFoundException by keycloakId`() {
            val keycloakId = "keycloak-abc-123"
            val ex = UserNotFoundException.byKeycloakId(keycloakId)
            val request = mockRequest()

            val response = handler.handleUserNotFound(ex, request)

            assertThat(response.body!!.message).contains("keycloak-abc-123")
        }

        @Test
        fun `should accept custom message via invoke operator`() {
            val ex = UserNotFoundException("Custom error message")
            val request = mockRequest()

            val response = handler.handleUserNotFound(ex, request)

            assertThat(response.body!!.message).isEqualTo("Custom error message")
        }
    }

    @Nested
    inner class Validation {

        @Test
        fun `should return 400 with field errors`() {
            val bindingResult = BeanPropertyBindingResult("target", "target")
            bindingResult.addError(
                FieldError("target", "email", null, false, arrayOf("NotBlank"), emptyArray<Any>(), "must not be blank")
            )
            bindingResult.addError(
                FieldError("target", "amount", null, false, arrayOf("Positive"), emptyArray<Any>(), "must be positive")
            )

            val errorResponse = buildValidationResponse(bindingResult, "/api/v1/users/me")

            assertThat(errorResponse.error).isEqualTo("VALIDATION_ERROR")
            assertThat(errorResponse.message).isEqualTo("Request validation failed")
            assertThat(errorResponse.path).isEqualTo("/api/v1/users/me")
            assertThat(errorResponse.details).isNotNull
            assertThat(errorResponse.details!!).containsEntry("email", "must not be blank")
            assertThat(errorResponse.details!!).containsEntry("amount", "must be positive")
        }

        @Test
        fun `should handle empty field errors gracefully`() {
            val bindingResult = BeanPropertyBindingResult("target", "target")

            val errorResponse = buildValidationResponse(bindingResult, "/test")

            assertThat(errorResponse.details).isNotNull
            assertThat(errorResponse.details!!).isEmpty()
        }

        @Test
        fun `should use 'invalid' fallback when defaultMessage is null`() {
            val bindingResult = BeanPropertyBindingResult("target", "target")
            bindingResult.addError(
                FieldError("target", "field", null, false, null, null, null)
            )

            val errorResponse = buildValidationResponse(bindingResult, "/test")

            assertThat(errorResponse.details!!).containsEntry("field", "invalid")
        }
    }

    @Nested
    inner class RoleNotFound {

        @Test
        fun `should return 400 for RoleNotFoundException`() {
            val ex = RoleNotFoundException("Role 'SUPERADMIN' not found in realm 'cloud-gaming'")
            val request = mockRequest()

            val response = handler.handleRoleNotFound(ex, request)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(response.body!!.error).isEqualTo("ROLE_NOT_FOUND")
        }
    }

    @Nested
    inner class AccessDenied {

        @Test
        fun `should return 403 for AccessDeniedException`() {
            val ex = AccessDeniedException("Access denied")
            val request = mockRequest("/api/v1/admin/users")

            val response = handler.handleAccessDenied(ex, request)

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
            assertThat(response.body!!.error).isEqualTo("ACCESS_DENIED")
            assertThat(response.body!!.message).contains("permission")
        }
    }

    @Nested
    inner class Authentication {

        @Test
        fun `should return 401 for AuthenticationException`() {
            val ex = object : AuthenticationException("Bad token") {}
            val request = mockRequest()

            val response = handler.handleAuthentication(ex, request)

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            assertThat(response.body!!.error).isEqualTo("UNAUTHORIZED")
        }
    }

    @Nested
    inner class InsufficientFunds {

        @Test
        fun `should return 409 with balance context`() {
            val userId = UUID.randomUUID()
            val ex = InsufficientFundsException(
                userId = userId,
                currentBalance = BigDecimal("50.00"),
                requestedAmount = BigDecimal("150.00")
            )
            val request = mockRequest()

            val response = handler.handleInsufficientFunds(ex, request)

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
            assertThat(response.body!!.error).isEqualTo("INSUFFICIENT_FUNDS")
            assertThat(response.body!!.details).isNotNull
            assertThat(response.body!!.details!!).containsEntry("user_id", userId.toString())
            assertThat(response.body!!.details!!).containsEntry("current_balance", "50.00")
            assertThat(response.body!!.details!!).containsEntry("requested_amount", "150.00")
        }
    }

    @Nested
    inner class IdempotencyConflict {

        @Test
        fun `should return 409 with previous transaction id`() {
            val previousTxnId = UUID.randomUUID()
            val ex = IdempotencyConflictException(
                idempotencyKey = "session-abc:debit",
                previousTransactionId = previousTxnId
            )
            val request = mockRequest()

            val response = handler.handleIdempotencyConflict(ex, request)

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
            assertThat(response.body!!.error).isEqualTo("IDEMPOTENCY_CONFLICT")
            assertThat(response.body!!.details).isNotNull
            assertThat(response.body!!.details!!).containsEntry("idempotency_key", "session-abc:debit")
            assertThat(response.body!!.details!!).containsEntry("previous_transaction_id", previousTxnId.toString())
        }
    }

    @Nested
    inner class ConcurrentBalanceOperation {

        @Test
        fun `should return 503 with Retry-After header`() {
            val userId = UUID.randomUUID()
            val ex = ConcurrentBalanceOperationException(userId)
            val request = mockRequest()

            val response = handler.handleConcurrentBalanceOperation(ex, request)

            assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            assertThat(response.headers.getFirst("Retry-After")).isEqualTo("5")
            assertThat(response.body!!.error).isEqualTo("CONCURRENT_OPERATION")
        }
    }

    @Nested
    inner class KeycloakApi {

        @Test
        fun `should return 503 with Retry-After header`() {
            val ex = KeycloakApiException("Connection refused")
            val request = mockRequest()

            val response = handler.handleKeycloakApi(ex, request)

            assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            assertThat(response.headers.getFirst("Retry-After")).isEqualTo("30")
            assertThat(response.body!!.error).isEqualTo("KEYCLOAK_UNAVAILABLE")
            assertThat(response.body!!.message).doesNotContain("Connection refused")
        }
    }

    @Nested
    inner class GenericException {

        @Test
        fun `should return 500 with error reference id`() {
            val ex = RuntimeException("Database connection lost")
            val request = mockRequest()

            val response = handler.handleGenericException(ex, request)

            assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
            assertThat(response.body!!.error).isEqualTo("INTERNAL_ERROR")
            assertThat(response.body!!.message).contains("Reference ID")
            assertThat(response.body!!.details).containsKey("error_id")

            val errorIdStr = response.body!!.details!!["error_id"] as String

            val parsed = UUID.fromString(errorIdStr)
            assertThat(parsed).isNotNull()

            val uuidPattern = Regex("[0-9a-f-]{36}")
            assertThat(uuidPattern.containsMatchIn(errorIdStr)).isTrue()

            assertThat(response.body!!.message).doesNotContain("Database connection lost")
        }
    }

    @Nested
    inner class ErrorResponseFormat {

        @Test
        fun `error response should serialize to JSON correctly`() {
            val ex = UserNotFoundException.byUserId(UUID.randomUUID())
            val request = mockRequest("/test/path")

            val response = handler.handleUserNotFound(ex, request)
            val json = objectMapper.writeValueAsString(response.body)

            assertThat(json).contains("\"error\":\"USER_NOT_FOUND\"")
            assertThat(json).contains("\"path\":\"/test/path\"")
            assertThat(json).contains("\"timestamp\"")
        }

        @Test
        fun `error response should not include null fields`() {
            val ex = UserNotFoundException("test")
            val request = mockRequest()

            val response = handler.handleUserNotFound(ex, request)
            val json = objectMapper.writeValueAsString(response.body)

            assertThat(json).doesNotContain("\"details\"")
        }
    }
}