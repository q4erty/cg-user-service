package com.cloudgaming.userservice.common.filter

import com.cloudgaming.userservice.service.UserProvisioningService
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

class UserProvisioningFilterTest {

    private val provisioningService: UserProvisioningService = mock()
    private val filter = UserProvisioningFilter(provisioningService)
    private val chain: FilterChain = mock()

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun buildJwt(
        subject: String = "keycloak-123",
        email: String? = "user@example.com",
        preferredUsername: String? = "user_name"
    ): Jwt {
        val claims = mutableMapOf<String, Any>("realm_access" to mapOf("roles" to listOf("PLAYER")))
        if (email != null) claims["email"] = email
        if (preferredUsername != null) claims["preferred_username"] = preferredUsername

        return Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject(subject)
            .claims { it.putAll(claims) }
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
    }

    @Nested
    inner class WithJwtAuthentication {

        @Test
        fun `should call validateUserExists when JWT is present`() {
            val jwt = buildJwt(subject = "kc-abc", email = "user@test.com", preferredUsername = "user")
            SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)

            val request = MockHttpServletRequest("GET", "/api/v1/users/me")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            verify(provisioningService).validateUserExists(eq("kc-abc"), eq("user@test.com"))
            verify(chain).doFilter(request, response)
        }

        @Test
        fun `should continue filter chain even if provisioning fails`() {
            val jwt = buildJwt(subject = "kc-abc")
            SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
            whenever(provisioningService.validateUserExists(any(), any()))
                .thenThrow(RuntimeException("DB down"))

            val request = MockHttpServletRequest("GET", "/api/v1/users/me")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            verify(chain).doFilter(request, response)
        }

        @Test
        fun `should handle null email in JWT`() {
            val jwt = buildJwt(subject = "kc-abc", email = null, preferredUsername = null)
            SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)

            val request = MockHttpServletRequest("GET", "/api/v1/users/me")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            verify(provisioningService).validateUserExists(eq("kc-abc"), eq(null))
        }

        @Test
        fun `should not call provisioning when JWT subject is null`() {
            val jwt = Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .claims { it["realm_access"] = mapOf("roles" to listOf("PLAYER")) }
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build()
            SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)

            val request = MockHttpServletRequest("GET", "/api/v1/users/me")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            verify(provisioningService, never()).validateUserExists(any(), any())
            verify(chain).doFilter(request, response)
        }
    }

    @Nested
    inner class WithoutJwtAuthentication {

        @Test
        fun `should not call provisioning when authentication is not JwtAuthenticationToken`() {
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                "internal-service", null,
                listOf(SimpleGrantedAuthority("ROLE_INTERNAL"))
            )

            val request = MockHttpServletRequest("POST", "/api/internal/users/123/balance/operations")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            verifyNoInteractions(provisioningService)
            verify(chain).doFilter(request, response)
        }

        @Test
        fun `should not call provisioning when no authentication in context`() {
            val request = MockHttpServletRequest("GET", "/actuator/health")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            verifyNoInteractions(provisioningService)
            verify(chain).doFilter(request, response)
        }
    }
}