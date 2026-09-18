package com.cloudgaming.userservice.controller

import com.cloudgaming.userservice.common.exception.KeycloakApiException
import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.common.security.SecurityUtils
import com.cloudgaming.userservice.config.SecurityConfig
import com.cloudgaming.userservice.constants.Role
import com.cloudgaming.userservice.domain.BalanceTransaction
import com.cloudgaming.userservice.domain.TransactionType
import com.cloudgaming.userservice.domain.User
import com.cloudgaming.userservice.domain.UserBalance
import com.cloudgaming.userservice.dto.BalanceOperationRequest
import com.cloudgaming.userservice.dto.BalanceOperationResponse
import com.cloudgaming.userservice.dto.UserRoleChangedEvent
import com.cloudgaming.userservice.dto.admin.BalanceAdjustRequest
import com.cloudgaming.userservice.dto.admin.RoleUpdateRequest
import com.cloudgaming.userservice.integration.keycloak.KeycloakRoleConverter
import com.cloudgaming.userservice.persistence.BalanceTransactionRepository
import com.cloudgaming.userservice.persistence.UserBalanceRepository
import com.cloudgaming.userservice.persistence.UserRepository
import com.cloudgaming.userservice.service.BalanceService
import com.cloudgaming.userservice.service.RoleManagementService
import com.cloudgaming.userservice.service.UserProvisioningService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@WebMvcTest(AdminUserController::class)
@Import(SecurityConfig::class)
class AdminUserControllerTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var roleManagementService: RoleManagementService

    @MockitoBean
    private lateinit var userRepository: UserRepository

    @MockitoBean
    private lateinit var userBalanceRepository: UserBalanceRepository

    @MockitoBean
    private lateinit var balanceTransactionRepository: BalanceTransactionRepository

    @MockitoBean
    private lateinit var balanceService: BalanceService

    @MockitoBean
    private lateinit var securityUtils: SecurityUtils

    @MockitoBean
    private lateinit var keycloakRoleConverter: KeycloakRoleConverter

    @MockitoBean
    private lateinit var userProvisioningService: UserProvisioningService

    private val testUserId = UUID.randomUUID()
    private val adminUserId = UUID.randomUUID()
    private val testKeycloakId = "keycloak-user-123"

    @BeforeEach
    fun setUp() {
        whenever(securityUtils.getCurrentUserId()).thenReturn(adminUserId)
    }

    private fun adminJwt() = SecurityMockMvcRequestPostProcessors.jwt()
        .authorities { listOf(SimpleGrantedAuthority("ROLE_ADMIN")) }

    private fun playerJwt() = SecurityMockMvcRequestPostProcessors.jwt()
        .authorities { listOf(SimpleGrantedAuthority("ROLE_PLAYER")) }

    private fun testUser() = User(
        id = testUserId,
        keycloakId = testKeycloakId,
        email = "test@example.com",
        displayName = "Test User",
        avatarUrl = "https://example.com/avatar.png",
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        lastLoginAt = Instant.parse("2026-06-19T12:00:00Z")
    )

    private fun testBalance() = UserBalance(
        userId = testUserId,
        amount = BigDecimal("1000.00"),
        currency = "RUB",
        version = 1L,
        lastOperationAt = Instant.parse("2026-06-19T12:00:00Z")
    )

    private fun testBalanceTransaction() = BalanceTransaction(
        id = UUID.randomUUID(),
        userId = testUserId,
        amount = BigDecimal("100.00"),
        type = TransactionType.DEPOSIT,
        sessionId = null,
        paymentId = UUID.randomUUID(),
        idempotencyKey = "admin:$adminUserId:test",
        createdAt = Instant.now(),
        description = "Test transaction"
    )

    @Nested
    inner class PatchRoleTests {

        @Test
        fun `PATCH role with role=PREMIUM, action=ADD, ADMIN auth returns 204 No Content`() {
            val userId = UUID.randomUUID()
            val roleUpdateRequest = RoleUpdateRequest(role = "PREMIUM", action = "ADD")

            whenever(roleManagementService.assignRole(userId, Role.PREMIUM, adminUserId))
                .thenReturn(
                    UserRoleChangedEvent(
                        targetUserId = userId,
                        performedByAdminId = adminUserId,
                        role = "PREMIUM",
                        action = "ADD"
                    )
                )

            mockMvc.perform(
                patch("/api/v1/admin/users/$userId/role")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(roleUpdateRequest))
                    .with(adminJwt())
            )
                .andExpect(status().isNoContent)

            verify(roleManagementService).assignRole(userId, Role.PREMIUM, adminUserId)
        }

        @Test
        fun `PATCH role without ADMIN role returns 403 Forbidden`() {
            val userId = UUID.randomUUID()
            val roleUpdateRequest = RoleUpdateRequest(role = "PREMIUM", action = "ADD")

            mockMvc.perform(
                patch("/api/v1/admin/users/$userId/role")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(roleUpdateRequest))
                    .with(playerJwt())
            )
                .andExpect(status().isForbidden)

            verify(roleManagementService, never()).assignRole(any(), any(), any())
        }

        @Test
        fun `PATCH role with invalid role (SUPERADMIN) returns 400`() {
            val userId = UUID.randomUUID()
            val roleUpdateRequest = RoleUpdateRequest(role = "SUPERADMIN", action = "ADD")

            mockMvc.perform(
                patch("/api/v1/admin/users/$userId/role")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(roleUpdateRequest))
                    .with(adminJwt())
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))

            verify(roleManagementService, never()).assignRole(any(), any(), any())
        }

        @Test
        fun `PATCH role with invalid action (TOGGLE) returns 400`() {
            val userId = UUID.randomUUID()
            val roleUpdateRequest = RoleUpdateRequest(role = "PREMIUM", action = "TOGGLE")

            mockMvc.perform(
                patch("/api/v1/admin/users/$userId/role")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(roleUpdateRequest))
                    .with(adminJwt())
            )
                .andExpect(status().isBadRequest)

            verify(roleManagementService, never()).assignRole(any(), any(), any())
        }

        @Test
        fun `PATCH role for nonexistent userId returns 404`() {
            val userId = UUID.randomUUID()
            val roleUpdateRequest = RoleUpdateRequest(role = "PREMIUM", action = "ADD")

            whenever(roleManagementService.assignRole(userId, Role.PREMIUM, adminUserId))
                .doThrow(UserNotFoundException.byUserId(userId))

            mockMvc.perform(
                patch("/api/v1/admin/users/$userId/role")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(roleUpdateRequest))
                    .with(adminJwt())
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"))

            verify(roleManagementService).assignRole(userId, Role.PREMIUM, adminUserId)
        }

        @Test
        fun `PATCH role when Keycloak is unavailable returns 503 + Retry-After header`() {
            val userId = UUID.randomUUID()
            val roleUpdateRequest = RoleUpdateRequest(role = "PREMIUM", action = "ADD")

            whenever(roleManagementService.assignRole(userId, Role.PREMIUM, adminUserId))
                .doThrow(KeycloakApiException("Keycloak is currently unavailable"))

            mockMvc.perform(
                patch("/api/v1/admin/users/$userId/role")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(roleUpdateRequest))
                    .with(adminJwt())
            )
                .andExpect(status().isServiceUnavailable)
                .andExpect(jsonPath("$.error").value("KEYCLOAK_UNAVAILABLE"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))

            verify(roleManagementService).assignRole(userId, Role.PREMIUM, adminUserId)
        }

        @Test
        fun `PATCH role with role=INTERNAL returns 400`() {
            val userId = UUID.randomUUID()
            val roleUpdateRequest = RoleUpdateRequest(role = "INTERNAL", action = "ADD")

            // Так как @Pattern в DTO не пропустит "INTERNAL", метод контроллера не вызовется
            mockMvc.perform(
                patch("/api/v1/admin/users/$userId/role")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(roleUpdateRequest))
                    .with(adminJwt())
            )
                .andExpect(status().isBadRequest)

            verify(roleManagementService, never()).assignRole(any(), any(), any())
        }

        @Test
        fun `PATCH role with empty body returns 400`() {
            val userId = UUID.randomUUID()

            mockMvc.perform(
                patch("/api/v1/admin/users/$userId/role")
                    .contentType("application/json")
                    .content("{}")
                    .with(adminJwt())
            )
                .andExpect(status().isBadRequest)

            verify(roleManagementService, never()).assignRole(any(), any(), any())
        }

        @Test
        fun `PATCH role with action=REMOVE calls removeRole`() {
            val userId = UUID.randomUUID()
            val roleUpdateRequest = RoleUpdateRequest(role = "PREMIUM", action = "REMOVE")

            whenever(roleManagementService.removeRole(userId, Role.PREMIUM, adminUserId))
                .thenReturn(
                    UserRoleChangedEvent(
                        targetUserId = userId,
                        performedByAdminId = adminUserId,
                        role = "PREMIUM",
                        action = "REMOVE"
                    )
                )

            mockMvc.perform(
                patch("/api/v1/admin/users/$userId/role")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(roleUpdateRequest))
                    .with(adminJwt())
            )
                .andExpect(status().isNoContent)

            verify(roleManagementService).removeRole(userId, Role.PREMIUM, adminUserId)
        }
    }

    @Nested
    inner class ListUsersTests {

        @Test
        fun `GET users without parameters returns 200, default page=0, size=20`() {
            val user = testUser()
            val balance = testBalance()

            val pageableCaptor = argumentCaptor<Pageable>()
            whenever(userRepository.findAll(pageableCaptor.capture())).thenReturn(
                PageImpl(listOf(user), PageRequest.of(0, 20), 1)
            )
            whenever(userBalanceRepository.findByUserId(testUserId)).thenReturn(balance)

            mockMvc.perform(get("/api/v1/admin/users").with(adminJwt()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(user.id.toString()))
                .andExpect(jsonPath("$.content[0].email").value("test@example.com"))
                .andExpect(jsonPath("$.content[0].balance").value(1000.00))

            assertThat(pageableCaptor.firstValue.pageNumber).isEqualTo(0)
            assertThat(pageableCaptor.firstValue.pageSize).isEqualTo(20)
        }

        @Test
        fun `GET users with email filter returns 200 with filtered list`() {
            val user = testUser()
            val balance = testBalance()

            whenever(userRepository.findByEmailContainingIgnoreCase(eq("test"), any<Pageable>())).thenReturn(
                PageImpl(listOf(user), PageRequest.of(0, 20), 1)
            )
            whenever(userBalanceRepository.findByUserId(testUserId)).thenReturn(balance)

            mockMvc.perform(get("/api/v1/admin/users").param("email", "test").with(adminJwt()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))

            verify(userRepository).findByEmailContainingIgnoreCase(eq("test"), any())
        }

        @Test
        fun `GET users with size=200 returns 400`() {
            mockMvc.perform(get("/api/v1/admin/users").param("size", "200").with(adminJwt()))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `GET users without ADMIN role returns 403`() {
            mockMvc.perform(get("/api/v1/admin/users").with(playerJwt()))
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class GetUserByIdTests {

        @Test
        fun `GET user by id returns 200 with AdminUserDto`() {
            val user = testUser()
            val balance = testBalance()

            whenever(userRepository.findById(testUserId)).thenReturn(Optional.of(user))
            whenever(userBalanceRepository.findByUserId(testUserId)).thenReturn(balance)

            mockMvc.perform(get("/api/v1/admin/users/$testUserId").with(adminJwt()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(user.id.toString()))
                .andExpect(jsonPath("$.keycloak_id").value(testKeycloakId))
                .andExpect(jsonPath("$.balance").value(1000.00))
                .andExpect(jsonPath("$.currency").value("RUB"))
        }

        @Test
        fun `GET user by id for nonexistent user returns 404`() {
            whenever(userRepository.findById(testUserId)).thenReturn(Optional.empty())

            mockMvc.perform(get("/api/v1/admin/users/$testUserId").with(adminJwt()))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"))
        }
    }

    @Nested
    inner class GetTransactionsTests {

        @Test
        fun `GET transactions for existing user returns 200 with paginated history`() {
            val transaction = testBalanceTransaction()

            whenever(userRepository.existsById(testUserId)).thenReturn(true)
            whenever(balanceTransactionRepository.findByUserIdOrderByCreatedAtDesc(eq(testUserId), any<Pageable>()))
                .thenReturn(PageImpl(listOf(transaction), PageRequest.of(0, 20), 1))

            mockMvc.perform(
                get("/api/v1/admin/users/$testUserId/balance/transactions").with(adminJwt())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].id").value(transaction.id.toString()))
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"))
        }

        @Test
        fun `GET transactions for nonexistent user returns 404`() {
            whenever(userRepository.existsById(testUserId)).thenReturn(false)

            mockMvc.perform(
                get("/api/v1/admin/users/$testUserId/balance/transactions").with(adminJwt())
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"))

            verify(balanceTransactionRepository, never()).findByUserIdOrderByCreatedAtDesc(any<UUID>(), any<Pageable>())
        }
    }

    @Nested
    inner class AdjustBalanceTests {

        @Test
        fun `POST adjust with amount=500, description=Compensation for failed session returns 200 with BalanceOperationResponse`() {
            val userId = UUID.randomUUID()
            val request = BalanceAdjustRequest(
                amount = BigDecimal("500.00"),
                description = "Compensation for failed session"
            )
            val response = BalanceOperationResponse(
                transactionId = UUID.randomUUID(),
                newBalance = BigDecimal("1500.00"),
                currency = "RUB",
                processedAt = Instant.now()
            )

            whenever(balanceService.applyOperation(eq(userId), any<BalanceOperationRequest>())).thenReturn(response)

            mockMvc.perform(
                post("/api/v1/admin/users/$userId/balance/adjust")
                    .header("X-Idempotency-Key", "test-idempotency-key-123")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request))
                    .with(adminJwt())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.new_balance").value(1500.00))

            verify(balanceService).applyOperation(eq(userId), any())
        }

        @Test
        fun `POST adjust with amount=-500 (negative) returns 200`() {
            val userId = UUID.randomUUID()
            val request = BalanceAdjustRequest(
                amount = BigDecimal("-500.00"),
                description = "Manual adjustment for user"
            )
            val response = BalanceOperationResponse(
                transactionId = UUID.randomUUID(),
                newBalance = BigDecimal("500.00"),
                currency = "RUB",
                processedAt = Instant.now()
            )

            whenever(balanceService.applyOperation(eq(userId), any<BalanceOperationRequest>())).thenReturn(response)

            mockMvc.perform(
                post("/api/v1/admin/users/$userId/balance/adjust")
                    .header("X-Idempotency-Key", "test-idempotency-key-123")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request))
                    .with(adminJwt())
            )
                .andExpect(status().isOk)

            verify(balanceService).applyOperation(eq(userId), any())
        }

        @Test
        fun `POST adjust without description returns 400`() {
            val userId = UUID.randomUUID()
            val request = BalanceAdjustRequest(
                amount = BigDecimal("500.00"),
                description = ""
            )

            mockMvc.perform(
                post("/api/v1/admin/users/$userId/balance/adjust")
                    .header("X-Idempotency-Key", "test-idempotency-key-123")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request))
                    .with(adminJwt())
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `POST adjust with description under 10 characters returns 400`() {
            val userId = UUID.randomUUID()
            val request = BalanceAdjustRequest(
                amount = BigDecimal("500.00"),
                description = "Short"
            )

            mockMvc.perform(
                post("/api/v1/admin/users/$userId/balance/adjust")
                    .header("X-Idempotency-Key", "test-idempotency-key-123")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request))
                    .with(adminJwt())
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `POST adjust with amount=0 returns 400`() {
            val userId = UUID.randomUUID()

            val jsonPayload = """
                {
                    "amount": 0.00,
                    "description": "Test adjustment description"
                }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v1/admin/users/$userId/balance/adjust")
                    .header("X-Idempotency-Key", "test-idempotency-key-123")
                    .contentType("application/json")
                    .content(jsonPayload)
                    .with(adminJwt())
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `POST adjust for nonexistent user returns 404`() {
            val userId = UUID.randomUUID()
            val request = BalanceAdjustRequest(
                amount = BigDecimal("500.00"),
                description = "Test adjustment description"
            )

            whenever(balanceService.applyOperation(eq(userId), any<BalanceOperationRequest>()))
                .doThrow(UserNotFoundException.byUserId(userId))

            mockMvc.perform(
                post("/api/v1/admin/users/$userId/balance/adjust")
                    .header("X-Idempotency-Key", "test-idempotency-key-123")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request))
                    .with(adminJwt())
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"))
        }

        @Test
        fun `POST adjust generates correct idempotency key`() {
            val userId = UUID.randomUUID()
            val request = BalanceAdjustRequest(
                amount = BigDecimal("500.00"),
                description = "Test adjustment description"
            )
            val response = BalanceOperationResponse(
                transactionId = UUID.randomUUID(),
                newBalance = BigDecimal("1500.00"),
                currency = "RUB",
                processedAt = Instant.now()
            )

            val captor = argumentCaptor<BalanceOperationRequest>()
            whenever(balanceService.applyOperation(eq(userId), captor.capture())).thenReturn(response)

            val expectedIdempotencyKey = "test-idempotency-key-123"

            mockMvc.perform(
                post("/api/v1/admin/users/$userId/balance/adjust")
                    .header("X-Idempotency-Key", expectedIdempotencyKey)
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request))
                    .with(adminJwt())
            )
                .andExpect(status().isOk)

            assertThat(captor.firstValue.idempotencyKey)
                .isEqualTo(expectedIdempotencyKey)
        }
    }
}