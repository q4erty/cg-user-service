package com.cloudgaming.userservice.controller

import com.cloudgaming.userservice.common.exception.ConcurrentBalanceOperationException
import com.cloudgaming.userservice.common.exception.IdempotencyConflictException
import com.cloudgaming.userservice.common.exception.InsufficientFundsException
import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.common.security.InternalSecretFilter
import com.cloudgaming.userservice.config.SecurityConfig
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
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@WebMvcTest(BalanceInternalController::class)
@Import(SecurityConfig::class)
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
    private lateinit var internalSecretFilter: InternalSecretFilter

    @MockitoBean
    private lateinit var keycloakRoleConverter: KeycloakRoleConverter

    @MockitoBean
    private lateinit var userProvisioningService: UserProvisioningService

    private val testUserId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        doAnswer { invocation ->
            val request = invocation.getArgument<HttpServletRequest>(0)
            val response = invocation.getArgument<HttpServletResponse>(1)
            val chain = invocation.getArgument<FilterChain>(2)
            chain.doFilter(request, response)
        }.`when`(internalSecretFilter).doFilter(any(), any(), any())
    }

    private fun internalJwt() = jwt().authorities { listOf(SimpleGrantedAuthority("ROLE_INTERNAL")) }

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
    fun `POST operations should return 200 with response`() {
        val request = testRequest()
        val response = testResponse()
        whenever(balanceService.applyOperation(testUserId, request)).thenReturn(response)

        mockMvc.perform(
            post("/api/internal/users/$testUserId/balance/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(internalJwt())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.transaction_id", `is`(response.transactionId.toString())))
            .andExpect(jsonPath("$.new_balance", `is`(200.00)))
    }

    @Test
    fun `POST operations should return 401 when not authenticated`() {
        val request = testRequest()

        mockMvc.perform(
            post("/api/internal/users/$testUserId/balance/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST operations should return 403 when missing INTERNAL role`() {
        val request = testRequest()

        mockMvc.perform(
            post("/api/internal/users/$testUserId/balance/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().authorities { listOf(SimpleGrantedAuthority("ROLE_PLAYER")) })
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `POST operations should return 400 when amount missing`() {
        val body = """{"type": "DEPOSIT", "idempotency_key": "key-1", "payment_id": "${UUID.randomUUID()}"}"""

        mockMvc.perform(
            post("/api/internal/users/$testUserId/balance/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(internalJwt())
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
                .with(internalJwt())
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
                .with(internalJwt())
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
                .with(internalJwt())
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
                .with(internalJwt())
        )
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.error", `is`("CONCURRENT_OPERATION")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("Retry-After"))
    }
}
