package com.cloudgaming.userservice.controller

import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.common.security.InternalSecretFilter
import com.cloudgaming.userservice.common.security.SecurityUtils
import com.cloudgaming.userservice.config.SecurityConfig
import com.cloudgaming.userservice.dto.UpdateProfileRequest
import com.cloudgaming.userservice.dto.UserProfileDto
import com.cloudgaming.userservice.integration.keycloak.KeycloakRoleConverter
import com.cloudgaming.userservice.service.ProfileService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@WebMvcTest(ProfileController::class)
@Import(SecurityConfig::class)
class ProfileControllerTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var profileService: ProfileService

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

    private fun testProfileDto() = UserProfileDto(
        id = testUserId,
        email = "alice@example.com",
        displayName = "Alice",
        avatarUrl = "https://example.com/avatar.png",
        balance = BigDecimal("150.00"),
        currency = "RUB",
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        lastLoginAt = Instant.parse("2026-06-19T12:00:00Z")
    )

    @Test
    fun `GET me should return 200 with profile`() {
        whenever(securityUtils.getCurrentUserId()).thenReturn(testUserId)
        whenever(profileService.getProfile(testUserId)).thenReturn(testProfileDto())

        mockMvc.perform(
            get("/api/v1/users/me").with(jwt().authorities {
                listOf(
                    SimpleGrantedAuthority(
                        "ROLE_PLAYER"
                    )
                )
            })
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id", `is`(testUserId.toString())))
            .andExpect(jsonPath("$.email", `is`("alice@example.com")))
            .andExpect(jsonPath("$.display_name", `is`("Alice")))
            .andExpect(jsonPath("$.avatar_url", `is`("https://example.com/avatar.png")))
            .andExpect(jsonPath("$.balance", `is`(150.00)))
            .andExpect(jsonPath("$.currency", `is`("RUB")))
    }

    @Test
    fun `GET me should return 404 when user not found`() {
        whenever(securityUtils.getCurrentUserId()).thenReturn(testUserId)
        whenever(profileService.getProfile(testUserId))
            .doThrow(UserNotFoundException.byUserId(testUserId))

        mockMvc.perform(
            get("/api/v1/users/me").with(jwt().authorities {
                listOf(
                    SimpleGrantedAuthority(
                        "ROLE_PLAYER"
                    )
                )
            })
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error", `is`("USER_NOT_FOUND")))
    }

    @Test
    fun `PATCH me should return 200 with updated profile`() {
        val request = UpdateProfileRequest(
            displayName = "Alice Updated",
            avatarUrl = "https://example.com/new-avatar.png"
        )
        val updatedDto = UserProfileDto(
            id = testUserId,
            email = "alice@example.com",
            displayName = "Alice Updated",
            avatarUrl = "https://example.com/new-avatar.png",
            balance = BigDecimal("150.00"),
            currency = "RUB",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            lastLoginAt = Instant.parse("2026-06-19T12:00:00Z")
        )

        whenever(securityUtils.getCurrentUserId()).thenReturn(testUserId)
        whenever(profileService.updateProfile(testUserId, request)).thenReturn(updatedDto)

        mockMvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().authorities { listOf(SimpleGrantedAuthority("ROLE_PLAYER")) })
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.display_name", `is`("Alice Updated")))
            .andExpect(jsonPath("$.avatar_url", `is`("https://example.com/new-avatar.png")))
    }

    @Test
    fun `PATCH me should return 400 when displayName is blank`() {
        val request = """{"display_name": "", "avatar_url": null}"""

        mockMvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
                .with(jwt().authorities { listOf(SimpleGrantedAuthority("ROLE_PLAYER")) })
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error", `is`("VALIDATION_ERROR")))
    }

    @Test
    fun `PATCH me should return 400 when avatarUrl is invalid URL`() {
        val request = """{"display_name": "Alice", "avatar_url": "not-a-url"}"""

        mockMvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
                .with(jwt().authorities { listOf(SimpleGrantedAuthority("ROLE_PLAYER")) })
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error", `is`("VALIDATION_ERROR")))
    }

    @Test
    fun `PATCH me should return 400 when displayName is missing`() {
        val request = """{"avatar_url": "https://example.com/a.png"}"""

        mockMvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
                .with(jwt().authorities { listOf(SimpleGrantedAuthority("ROLE_PLAYER")) })
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH me should accept null avatarUrl`() {
        val request = UpdateProfileRequest(displayName = "Alice", avatarUrl = null)
        val updatedDto = UserProfileDto(
            id = testUserId,
            email = "alice@example.com",
            displayName = "Alice",
            avatarUrl = null,
            balance = BigDecimal("150.00"),
            currency = "RUB",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            lastLoginAt = Instant.parse("2026-06-19T12:00:00Z")
        )

        whenever(securityUtils.getCurrentUserId()).thenReturn(testUserId)
        whenever(profileService.updateProfile(any(), any())).thenReturn(updatedDto)

        mockMvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().authorities { listOf(SimpleGrantedAuthority("ROLE_PLAYER")) })
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.avatar_url").doesNotExist())
    }

    @Test
    fun `PATCH me should return 404 when user not found`() {
        val request = UpdateProfileRequest(displayName = "Alice", avatarUrl = null)

        whenever(securityUtils.getCurrentUserId()).thenReturn(testUserId)
        whenever(profileService.updateProfile(any(), any()))
            .doThrow(UserNotFoundException.byUserId(testUserId))

        mockMvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().authorities { listOf(SimpleGrantedAuthority("ROLE_PLAYER")) })
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error", `is`("USER_NOT_FOUND")))
    }
}