package com.cloudgaming.userservice.controller

import com.cloudgaming.userservice.common.exception.TransactionNotFoundException
import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.common.security.InternalSecretFilter
import com.cloudgaming.userservice.common.security.SecurityUtils
import com.cloudgaming.userservice.config.SecurityConfig
import com.cloudgaming.userservice.domain.TransactionType
import com.cloudgaming.userservice.dto.BalanceDto
import com.cloudgaming.userservice.dto.TransactionDto
import com.cloudgaming.userservice.integration.keycloak.KeycloakRoleConverter
import com.cloudgaming.userservice.service.BalanceQueryService
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
import org.springframework.data.domain.PageImpl
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@WebMvcTest(BalanceController::class)
@Import(SecurityConfig::class)
class BalanceControllerTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var balanceQueryService: BalanceQueryService

    @MockitoBean
    private lateinit var securityUtils: SecurityUtils

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

    private fun playerJwt() = jwt().authorities { listOf(SimpleGrantedAuthority("ROLE_PLAYER")) }

    private fun testBalanceDto() = BalanceDto(
        amount = BigDecimal("150.00"),
        currency = "KZT",
        lastOperationAt = Instant.parse("2026-06-19T12:00:00Z")
    )

    private fun testTransactionDto(id: UUID = UUID.randomUUID()) = TransactionDto(
        id = id,
        amount = BigDecimal("50.00"),
        type = TransactionType.DEPOSIT.name,
        sessionId = null,
        paymentId = UUID.randomUUID(),
        createdAt = Instant.parse("2026-06-19T12:00:00Z"),
        description = "test deposit"
    )

    @Test
    fun `GET balance should return 200 with balance`() {
        whenever(securityUtils.getCurrentUserId()).thenReturn(testUserId)
        whenever(balanceQueryService.getBalance(testUserId)).thenReturn(testBalanceDto())

        mockMvc.perform(get("/api/v1/users/me/balance").with(playerJwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.amount", `is`(150.00)))
            .andExpect(jsonPath("$.currency", `is`("KZT")))
    }

    @Test
    fun `GET balance should return 404 when user not found`() {
        whenever(securityUtils.getCurrentUserId()).thenReturn(testUserId)
        whenever(balanceQueryService.getBalance(testUserId))
            .doThrow(UserNotFoundException.byUserId(testUserId))

        mockMvc.perform(get("/api/v1/users/me/balance").with(playerJwt()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error", `is`("USER_NOT_FOUND")))
    }

    @Test
    fun `GET balance should return 401 when not authenticated`() {
        mockMvc.perform(get("/api/v1/users/me/balance"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET transactions should return 200 with page`() {
        val transaction = testTransactionDto()
        whenever(securityUtils.getCurrentUserId()).thenReturn(testUserId)
        whenever(balanceQueryService.getTransactionHistory(testUserId, 0, 20, "createdAt,desc"))
            .thenReturn(PageImpl(listOf(transaction)))

        mockMvc.perform(get("/api/v1/users/me/balance/transactions").with(playerJwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id", `is`(transaction.id.toString())))
            .andExpect(jsonPath("$.content[0].type", `is`("DEPOSIT")))
    }

    @Test
    fun `GET transactions should pass custom page size and sort to service`() {
        whenever(securityUtils.getCurrentUserId()).thenReturn(testUserId)
        whenever(balanceQueryService.getTransactionHistory(testUserId, 2, 5, "createdAt,asc"))
            .thenReturn(PageImpl(emptyList()))

        mockMvc.perform(
            get("/api/v1/users/me/balance/transactions")
                .param("page", "2")
                .param("size", "5")
                .param("sort", "createdAt,asc")
                .with(playerJwt())
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `GET transactions should return 400 when service rejects parameters`() {
        whenever(securityUtils.getCurrentUserId()).thenReturn(testUserId)
        whenever(balanceQueryService.getTransactionHistory(testUserId, 0, 500, "createdAt,desc"))
            .doThrow(IllegalArgumentException("size must be between 1 and 100"))

        mockMvc.perform(
            get("/api/v1/users/me/balance/transactions")
                .param("size", "500")
                .with(playerJwt())
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error", `is`("INVALID_PARAMETER")))
    }

    @Test
    fun `GET transaction by id should return 200 with transaction`() {
        val transaction = testTransactionDto()
        whenever(securityUtils.getCurrentUserId()).thenReturn(testUserId)
        whenever(balanceQueryService.getTransactionById(testUserId, transaction.id)).thenReturn(transaction)

        mockMvc.perform(get("/api/v1/users/me/balance/transactions/${transaction.id}").with(playerJwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id", `is`(transaction.id.toString())))
            .andExpect(jsonPath("$.amount", `is`(50.00)))
    }

    @Test
    fun `GET transaction by id should return 404 when not found or not owned`() {
        val transactionId = UUID.randomUUID()
        whenever(securityUtils.getCurrentUserId()).thenReturn(testUserId)
        whenever(balanceQueryService.getTransactionById(testUserId, transactionId))
            .doThrow(TransactionNotFoundException(transactionId, testUserId))

        mockMvc.perform(get("/api/v1/users/me/balance/transactions/$transactionId").with(playerJwt()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error", `is`("TRANSACTION_NOT_FOUND")))
    }

    @Test
    fun `GET transaction by id should return 400 for malformed id`() {
        mockMvc.perform(get("/api/v1/users/me/balance/transactions/not-a-uuid").with(playerJwt()))
            .andExpect(status().isBadRequest)
    }
}
