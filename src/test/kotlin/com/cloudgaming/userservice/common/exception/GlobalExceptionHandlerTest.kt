package com.cloudgaming.userservice.common.exception

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.HttpInputMessage
import org.mockito.kotlin.mock
import java.lang.reflect.Method
import java.math.BigDecimal
import java.util.*
import java.util.Collections.singletonList

class GlobalExceptionHandlerTest {

    private class TestableGlobalExceptionHandler : GlobalExceptionHandler() {
        fun invokeHandleMethodArgumentNotValid(
            ex: MethodArgumentNotValidException,
            headers: HttpHeaders,
            status: HttpStatus,
            request: ServletWebRequest
        ) = handleMethodArgumentNotValid(ex, headers, status, request)

        fun invokeHandleNoHandlerFoundException(
            ex: NoHandlerFoundException,
            headers: HttpHeaders,
            status: HttpStatus,
            request: ServletWebRequest
        ) = handleNoHandlerFoundException(ex, headers, status, request)

        fun invokeHandleHttpRequestMethodNotSupported(
            ex: HttpRequestMethodNotSupportedException,
            headers: HttpHeaders,
            status: HttpStatus,
            request: ServletWebRequest
        ) = handleHttpRequestMethodNotSupported(ex, headers, status, request)

        fun invokeHandleHttpMediaTypeNotSupported(
            ex: HttpMediaTypeNotSupportedException,
            headers: HttpHeaders,
            status: HttpStatus,
            request: ServletWebRequest
        ) = handleHttpMediaTypeNotSupported(ex, headers, status, request)

        fun invokeHandleMissingServletRequestParameter(
            ex: MissingServletRequestParameterException,
            headers: HttpHeaders,
            status: HttpStatus,
            request: ServletWebRequest
        ) = handleMissingServletRequestParameter(ex, headers, status, request)

        fun invokeHandleHttpMessageNotReadable(
            ex: HttpMessageNotReadableException,
            headers: HttpHeaders,
            status: HttpStatus,
            request: ServletWebRequest
        ) = handleHttpMessageNotReadable(ex, headers, status, request)

        fun invokeHandleExceptionInternal(
            ex: Exception,
            body: Any?,
            headers: HttpHeaders,
            status: HttpStatus,
            request: ServletWebRequest
        ) = handleExceptionInternal(ex, body, headers, status, request)
    }

    private val handler = TestableGlobalExceptionHandler()
    private val objectMapper = ObjectMapper().apply {
        registerModule(JavaTimeModule())
    }

    private fun mockRequest(uri: String = "/api/v1/test"): MockHttpServletRequest {
        return MockHttpServletRequest().apply {
            requestURI = uri
        }
    }

    private fun ServletWebRequest(uri: String): ServletWebRequest {
        return ServletWebRequest(mockRequest(uri))
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
    }

    @Nested
    inner class NoHandlerFound {

        @Test
        fun `should return 404 for NoHandlerFoundException`() {
            val ex = NoHandlerFoundException("GET", "/api/v1/nonexistent", HttpHeaders())
            val webRequest = ServletWebRequest("/api/v1/nonexistent")

            val response = handler.invokeHandleNoHandlerFoundException(
                ex, HttpHeaders(), HttpStatus.NOT_FOUND, webRequest
            )

            assertThat(response?.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
            val errorResponse = response?.body as ErrorResponse
            assertThat(errorResponse.error).isEqualTo("NOT_FOUND")
            assertThat(errorResponse.message).contains("GET")
            assertThat(errorResponse.message).contains("/api/v1/nonexistent")
            assertThat(errorResponse.path).isEqualTo("/api/v1/nonexistent")
        }
    }

    @Nested
    inner class Validation {

        private fun dummyMethod(): Method = GlobalExceptionHandlerTest::class.java.methods[0]

        @Test
        fun `should return 400 with field errors`() {
            val bindingResult = BeanPropertyBindingResult(GlobalExceptionHandlerTest::class.java, "target")
            bindingResult.addError(
                FieldError("target", "email", null, false, arrayOf("NotBlank"), emptyArray<Any>(), "must not be blank")
            )
            bindingResult.addError(
                FieldError("target", "amount", null, false, arrayOf("Positive"), emptyArray<Any>(), "must be positive")
            )
            val methodParameter = MethodParameter(dummyMethod(), -1)
            val exception = MethodArgumentNotValidException(methodParameter, bindingResult)
            val webRequest = ServletWebRequest("/api/v1/users/me")

            val response = handler.invokeHandleMethodArgumentNotValid(
                exception, HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest
            )

            assertThat(response?.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            val errorResponse = response?.body as ErrorResponse
            assertThat(errorResponse.error).isEqualTo("VALIDATION_ERROR")
            assertThat(errorResponse.message).isEqualTo("Request validation failed")
            assertThat(errorResponse.path).isEqualTo("/api/v1/users/me")
            assertThat(errorResponse.details).isNotNull
            assertThat(errorResponse.details!!["email"]).isEqualTo(listOf("must not be blank"))
            assertThat(errorResponse.details!!["amount"]).isEqualTo(listOf("must be positive"))
        }

        @Test
        fun `should handle empty field errors gracefully`() {
            val bindingResult = BeanPropertyBindingResult(GlobalExceptionHandlerTest::class.java, "target")
            val methodParameter = MethodParameter(dummyMethod(), -1)
            val exception = MethodArgumentNotValidException(methodParameter, bindingResult)
            val webRequest = ServletWebRequest("/test")

            val response = handler.invokeHandleMethodArgumentNotValid(
                exception, HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest
            )

            assertThat(response?.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            val errorResponse = response?.body as ErrorResponse
            assertThat(errorResponse.details).isNotNull
            assertThat(errorResponse.details!!).isEmpty()
        }

        @Test
        fun `should use 'invalid' fallback when defaultMessage is null`() {
            val bindingResult = BeanPropertyBindingResult(GlobalExceptionHandlerTest::class.java, "target")
            bindingResult.addError(
                FieldError("target", "field", null, false, null, null, null)
            )
            val methodParameter = MethodParameter(dummyMethod(), -1)
            val exception = MethodArgumentNotValidException(methodParameter, bindingResult)
            val webRequest = ServletWebRequest("/test")

            val response = handler.invokeHandleMethodArgumentNotValid(
                exception, HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest
            )

            assertThat(response?.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            val errorResponse = response?.body as ErrorResponse
            assertThat(errorResponse.details!!["field"]).isEqualTo(listOf("invalid"))
        }

        @Test
        fun `should aggregate multiple errors for same field`() {
            val bindingResult = BeanPropertyBindingResult(GlobalExceptionHandlerTest::class.java, "target")
            bindingResult.addError(
                FieldError("target", "email", null, false, arrayOf("NotBlank"), emptyArray<Any>(), "must not be blank")
            )
            bindingResult.addError(
                FieldError("target", "email", null, false, arrayOf("Email"), emptyArray<Any>(), "must be a valid email")
            )
            val methodParameter = MethodParameter(dummyMethod(), -1)
            val exception = MethodArgumentNotValidException(methodParameter, bindingResult)
            val webRequest = ServletWebRequest("/api/v1/users")

            val response = handler.invokeHandleMethodArgumentNotValid(
                exception, HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest
            )

            assertThat(response?.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            val errorResponse = response?.body as ErrorResponse
            assertThat(errorResponse.details!!["email"]).isEqualTo(listOf("must not be blank", "must be a valid email"))
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
    inner class MethodNotSupported {

        @Test
        fun `should return 405 for HttpRequestMethodNotSupportedException`() {
            val ex = HttpRequestMethodNotSupportedException("DELETE")
            val webRequest = ServletWebRequest("/api/v1/users/123")

            val response = handler.invokeHandleHttpRequestMethodNotSupported(
                ex, HttpHeaders(), HttpStatus.METHOD_NOT_ALLOWED, webRequest
            )

            assertThat(response?.statusCode).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED)
            val errorResponse = response?.body as ErrorResponse
            assertThat(errorResponse.error).isEqualTo("METHOD_NOT_ALLOWED")
            assertThat(errorResponse.message).contains("DELETE")
            assertThat(errorResponse.path).isEqualTo("/api/v1/users/123")
        }
    }

    @Nested
    inner class MediaTypeNotSupported {

        @Test
        fun `should return 415 for HttpMediaTypeNotSupportedException`() {
            val rejectedMediaType = MediaType.parseMediaType("application/xml")
            val supportedMediaType = MediaType.parseMediaType("application/json")
            val ex = HttpMediaTypeNotSupportedException(rejectedMediaType, singletonList(supportedMediaType))
            val webRequest = ServletWebRequest("/api/v1/users")

            val response = handler.invokeHandleHttpMediaTypeNotSupported(
                ex, HttpHeaders(), HttpStatus.UNSUPPORTED_MEDIA_TYPE, webRequest
            )

            assertThat(response?.statusCode).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            val errorResponse = response?.body as ErrorResponse
            assertThat(errorResponse.error).isEqualTo("UNSUPPORTED_MEDIA_TYPE")
            assertThat(errorResponse.message).contains("application/xml")
            assertThat(errorResponse.path).isEqualTo("/api/v1/users")
        }
    }

    @Nested
    inner class MissingParameter {

        @Test
        fun `should return 400 for MissingServletRequestParameterException`() {
            val ex = MissingServletRequestParameterException("userId", "UUID")
            val webRequest = ServletWebRequest("/api/v1/users")

            val response = handler.invokeHandleMissingServletRequestParameter(
                ex, HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest
            )

            assertThat(response?.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            val errorResponse = response?.body as ErrorResponse
            assertThat(errorResponse.error).isEqualTo("MISSING_PARAMETER")
            assertThat(errorResponse.message).contains("userId")
            assertThat(errorResponse.path).isEqualTo("/api/v1/users")
        }
    }

    @Nested
    inner class MalformedJson {

        @Test
        fun `should return 400 for HttpMessageNotReadableException`() {
            val httpInputMessage = mock<HttpInputMessage>()
            val ex = HttpMessageNotReadableException("Could not read JSON: malformed input", null, httpInputMessage)
            val webRequest = ServletWebRequest("/api/v1/users/me")

            val response = handler.invokeHandleHttpMessageNotReadable(
                ex, HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest
            )

            assertThat(response?.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            val errorResponse = response?.body as ErrorResponse
            assertThat(errorResponse.error).isEqualTo("MALFORMED_JSON")
            assertThat(errorResponse.message).isEqualTo("Malformed JSON request")
            assertThat(errorResponse.path).isEqualTo("/api/v1/users/me")
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
            val ex = UserNotFoundException.byUserId(UUID.randomUUID())
            val request = mockRequest()

            val response = handler.handleUserNotFound(ex, request)
            val json = objectMapper.writeValueAsString(response.body)

            assertThat(json).doesNotContain("\"details\"")
        }
    }

    @Nested
    inner class ExceptionInternal {

        @Test
        fun `should return response with existing ErrorResponse body`() {
            val ex = RuntimeException("Something went wrong")
            val errorResponse = ErrorResponse(
                error = "CUSTOM_ERROR",
                message = "Custom message",
                path = "/api/v1/test"
            )
            val webRequest = ServletWebRequest("/api/v1/test")

            val response = handler.invokeHandleExceptionInternal(
                ex, errorResponse, HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(response.body).isEqualTo(errorResponse)
        }

        @Test
        fun `should wrap string body in ErrorResponse`() {
            val ex = RuntimeException()
            val webRequest = ServletWebRequest("/api/v1/test")

            val response = handler.invokeHandleExceptionInternal(
                ex, "Simple error message", HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(response.body).isInstanceOf(ErrorResponse::class.java)
            val body = response.body as ErrorResponse
            assertThat(body.message).isEqualTo("Simple error message")
            assertThat(body.path).isEqualTo("/api/v1/test")
        }

        @Test
        fun `should use exception message when body is not string or ErrorResponse`() {
            val ex = RuntimeException("Exception message here")
            val webRequest = ServletWebRequest("/api/v1/test")

            val response = handler.invokeHandleExceptionInternal(
                ex, null, HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, webRequest
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
            assertThat(response.body).isInstanceOf(ErrorResponse::class.java)
            val body = response.body as ErrorResponse
            assertThat(body.message).isEqualTo("Exception message here")
        }

        @Test
        fun `should use default message when exception has no message`() {
            val ex = RuntimeException()
            val webRequest = ServletWebRequest("/api/v1/test")

            val response = handler.invokeHandleExceptionInternal(
                ex, null, HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, webRequest
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
            assertThat(response.body).isInstanceOf(ErrorResponse::class.java)
            val body = response.body as ErrorResponse
            assertThat(body.message).isEqualTo("Unexpected error")
        }
    }
}