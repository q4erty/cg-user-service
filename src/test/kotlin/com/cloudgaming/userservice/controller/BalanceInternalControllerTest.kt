package com.cloudgaming.userservice.controller

import com.cloudgaming.userservice.common.exception.ConcurrentBalanceOperationException
import com.cloudgaming.userservice.common.exception.IdempotencyConflictException
import com.cloudgaming.userservice.common.exception.InsufficientFundsException
import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.common.security.InternalSecretFilter
import com.cloudgaming.userservice.config.SecurityConfig
import com.cloudgaming.userservice.constants.HeaderNames
import com.cloudgaming.userservice.domain.TransactionType
import com.cloudgaming.userservice.dto.BalanceOperationRequest
import com.cloudgaming.userservice.dto.BalanceOperationResponse
import com.cloudgaming.userservice.integration.keycloak.KeycloakRoleConverter
import com.cloudgaming.userservice.service.BalanceService
import com.cloudgaming.userservice.service.UserProvisioningService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * Uses the real [InternalSecretFilter] (instead of a passthrough mock) so the
 * X-Internal-Secret header is actually validated end-to-end: missing/invalid
 * secrets must be rejected with 401, and only a valid secret grants ROLE_INTERNAL.
 */
@WebMvcTest(BalanceInternalController::class)
@Import(SecurityConfig::class, InternalSecretFilter::class)
@TestPropertySource(properties = ["app.security.internal-secret=test-secret-12345"])
class BalanceInternalControllerTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var balanceService: BalanceService

    @MockitoBean
    private lateinit var keycloakRoleConverter: KeycloakRoleConverter

    @MockitoBean
    private lateinit var userProvisioningService: UserProvisioningService

    private val testUserId: UUID = UUID.randomUUID()
    private val validSecret = "test-secret-12345"

    private fun testRequest(
        type: TransactionType = TransactionType.DEPOSIT,
        amount: BigDecimal = BigDecimal("50.00"),
        idempotencyKey: String = "key-1",
        paymentId: UUID? = UUID.randomUUID()
    ) = BalanceOperationRequest(
        type = type,
        amount = amount,
        idempotencyKey = idempotencyKey,
        paymentId = paymentId
    )

    private fun testResponse() = BalanceOperationResponse(
        transactionId = UUID.randomUUID(),
        newBalance = BigDecimal("200.00"),
        currency = "KZT",
        processedAt = Instant.parse("2026-06-19T12:00:00Z")
    )

    @Test
    fun `POST operations should return 200 with response when secret is valid`() {
        val request = testRequest()
        val response = testResponse()
        whenever(balanceService.applyOperation(testUserId, request)).thenReturn(response)

        mockMvc.perform(
            post("/api/internal/users/$testUserId/balance/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header(HeaderNames.INTERNAL_SECRET, validSecret)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.transaction_id", `is`(response.transactionId.toString())))
            .andExpect(jsonPath("$.new_balance", `is`(200.00)))
    }

    @Test
    fun `POST operations should return 401 when X-Internal-Secret header is missing`() {
        val request = testRequest()

        mockMvc.perform(
            post("/api/internal/users/$testUserId/balance/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST operations should return 401 when X-Internal-Secret header is invalid`() {
        val request = testRequest()

        mockMvc.perform(
            post("/api/internal/users/$testUserId/balance/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header(HeaderNames.INTERNAL_SECRET, "wrong-secret")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST operations should return 400 when amount missing`() {
        val body = """{"type": "DEPOSIT", "idempotency_key": "key-1", "payment_id": "${UUID.randomUUID()}"}"""

        mockMvc.perform(
            post("/api/internal/users/$testUserId/balance/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(HeaderNames.INTERNAL_SECRET, validSecret)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST operations should return 404 when user not found`() {
        val request = testRequest()
        whenever(balanceService.applyOperation(testUserId, request))
            .doThrow(UserNotFoundException.byUserId(testUserId))

        mockMvc.perform(
            post("/api/internal/users/$testUserId/balance/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header(HeaderNames.INTERNAL_SECRET, validSecret)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error", `is`("USER_NOT_FOUND")))
    }

    @Test
    fun `POST operations should return 409 when insufficient funds`() {
        val request = testRequest(type = TransactionType.SESSION_DEBIT, amount = BigDecimal("1000.00"))
            .let { it.copy(paymentId = null, sessionId = UUID.randomUUID()) }
        whenever(balanceService.applyOperation(testUserId, request))
            .doThrow(InsufficientFundsException(testUserId, BigDecimal("10.00"), BigDecimal("1000.00")))

        mockMvc.perform(
            post("/api/internal/users/$testUserId/balance/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header(HeaderNames.INTERNAL_SECRET, validSecret)
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error", `is`("INSUFFICIENT_FUNDS")))
    }

    @Test
    fun `POST operations should return 409 when idempotency conflict`() {
        val request = testRequest()
        whenever(balanceService.applyOperation(testUserId, request))
            .doThrow(IdempotencyConflictException(request.idempotencyKey, UUID.randomUUID()))

        mockMvc.perform(
            post("/api/internal/users/$testUserId/balance/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header(HeaderNames.INTERNAL_SECRET, validSecret)
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error", `is`("IDEMPOTENCY_CONFLICT")))
    }

    @Test
    fun `POST operations should return 503 when concurrent operation in progress`() {
        val request = testRequest()
        whenever(balanceService.applyOperation(testUserId, request))
            .doThrow(ConcurrentBalanceOperationException(testUserId))

        mockMvc.perform(
            post("/api/internal/users/$testUserId/balance/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header(HeaderNames.INTERNAL_SECRET, validSecret)
        )
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.error", `is`("CONCURRENT_OPERATION")))
            .andExpect(header().exists("Retry-After"))
    }
}
