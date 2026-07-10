package com.cloudgaming.userservice.common.security

import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.constants.Role
import com.cloudgaming.userservice.service.UserProvisioningService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class SecurityUtilsTest {

    @Mock
    private lateinit var userProvisioningService: UserProvisioningService

    @InjectMocks
    private lateinit var securityUtils: SecurityUtils

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
        preferredUsername: String? = "user_name",
        roles: List<String> = listOf("PLAYER")
    ): Jwt {
        val claims = mutableMapOf<String, Any>(
            "realm_access" to mapOf("roles" to roles)
        )
        if (email != null) claims["email"] = email
        if (preferredUsername != null) claims["preferred_username"] = preferredUsername

        val builder = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .claims { it.putAll(claims) }
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))

        if (subject.isNotEmpty()) {
            builder.subject(subject)
        }

        return builder.build()
    }

    private fun setJwtAuth(jwt: Jwt, authorities: List<String> = listOf("ROLE_PLAYER")) {
        val auth = JwtAuthenticationToken(
            jwt,
            authorities.map { SimpleGrantedAuthority(it) }
        )
        SecurityContextHolder.getContext().authentication = auth
    }

    @Nested
    inner class GetCurrentKeycloakId {

        @Test
        fun `should return subject from JWT`() {
            val jwt = buildJwt(subject = "keycloak-abc-123")
            setJwtAuth(jwt)

            val keycloakId = securityUtils.getCurrentKeycloakId()

            assertThat(keycloakId).isEqualTo("keycloak-abc-123")
        }

        @Test
        fun `should throw when no authentication in context`() {
            assertThatThrownBy { securityUtils.getCurrentKeycloakId() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("No authentication")
        }

        @Test
        fun `should throw when authentication is not JwtAuthenticationToken`() {
            val auth = UsernamePasswordAuthenticationToken(
                "user", "pass",
                listOf(SimpleGrantedAuthority("ROLE_PLAYER"))
            )
            SecurityContextHolder.getContext().authentication = auth

            assertThatThrownBy { securityUtils.getCurrentKeycloakId() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Expected JwtAuthenticationToken")
        }

        @Test
        fun `should throw when JWT subject is null`() {
            val jwt = buildJwt(subject = "")
            setJwtAuth(jwt)

            assertThatThrownBy { securityUtils.getCurrentKeycloakId() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("subject")
        }
    }

    @Nested
    inner class GetCurrentUserId {

        @Test
        fun `should return UUID when provisioning service returns it`() {
            val testUuid = UUID.randomUUID()
            val jwt = buildJwt(subject = "keycloak-id-123")
            setJwtAuth(jwt)
            whenever(userProvisioningService.requireInternalUserId("keycloak-id-123"))
                .thenReturn(testUuid)

            val userId = securityUtils.getCurrentUserId()

            assertThat(userId).isEqualTo(testUuid)
        }

        @Test
        fun `should throw UserNotFoundException when provisioning returns null`() {
            val jwt = buildJwt(subject = "unknown-keycloak-id")
            setJwtAuth(jwt)
            whenever(userProvisioningService.requireInternalUserId("unknown-keycloak-id"))
                .thenThrow(UserNotFoundException.byKeycloakId("unknown-keycloak-id"))

            assertThatThrownBy { securityUtils.getCurrentUserId() }
                .isInstanceOf(UserNotFoundException::class.java)
        }

        @Test
        fun `should throw when no authentication in context`() {
            assertThatThrownBy { securityUtils.getCurrentUserId() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("No authentication")
        }

        @Test
        fun `should throw when authentication is not JwtAuthenticationToken`() {
            val auth = UsernamePasswordAuthenticationToken(
                "user", "pass",
                listOf(SimpleGrantedAuthority(Role.PLAYER.authority))
            )
            SecurityContextHolder.getContext().authentication = auth

            assertThatThrownBy { securityUtils.getCurrentUserId() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Expected JwtAuthenticationToken")
        }

        @Test
        fun `should throw when JWT subject is null`() {
            val jwt = buildJwt(subject = "")
            setJwtAuth(jwt)

            assertThatThrownBy { securityUtils.getCurrentUserId() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("subject")
        }
    }

    @Nested
    inner class GetCurrentEmail {

        @Test
        fun `should return email from JWT`() {
            val jwt = buildJwt(email = "alice@example.com")
            setJwtAuth(jwt)

            assertThat(securityUtils.getCurrentEmail()).isEqualTo("alice@example.com")
        }

        @Test
        fun `should return null when email claim is missing`() {
            val jwt = buildJwt(email = null)
            setJwtAuth(jwt)

            assertThat(securityUtils.getCurrentEmail()).isNull()
        }
    }

    @Nested
    inner class GetCurrentUsername {

        @Test
        fun `should return preferred_username from JWT`() {
            val jwt = buildJwt(preferredUsername = "alice_player")
            setJwtAuth(jwt)

            assertThat(securityUtils.getCurrentUsername()).isEqualTo("alice_player")
        }

        @Test
        fun `should return null when preferred_username is missing`() {
            val jwt = buildJwt(preferredUsername = null)
            setJwtAuth(jwt)

            assertThat(securityUtils.getCurrentUsername()).isNull()
        }
    }

    @Nested
    inner class HasRole {

        @Test
        fun `should return true when user has role`() {
            val jwt = buildJwt()
            setJwtAuth(jwt, authorities = listOf(Role.PLAYER.authority, Role.PREMIUM.authority))

            assertThat(securityUtils.hasRole(Role.PLAYER)).isTrue
            assertThat(securityUtils.hasRole(Role.PREMIUM)).isTrue
        }

        @Test
        fun `should return false when user does not have role`() {
            val jwt = buildJwt()
            setJwtAuth(jwt, authorities = listOf(Role.PLAYER.authority))

            assertThat(securityUtils.hasRole(Role.ADMIN)).isFalse
        }

        @Test
        fun `should return false when no authentication`() {
            assertThat(securityUtils.hasRole(Role.PLAYER)).isFalse
        }
    }

    @Nested
    inner class IsJwtAuthenticated {

        @Test
        fun `should return true for JwtAuthenticationToken`() {
            val jwt = buildJwt()
            setJwtAuth(jwt)

            assertThat(securityUtils.isJwtAuthenticated()).isTrue
        }

        @Test
        fun `should return false for UsernamePasswordAuthenticationToken`() {
            val auth = UsernamePasswordAuthenticationToken("user", "pass", emptyList())
            SecurityContextHolder.getContext().authentication = auth

            assertThat(securityUtils.isJwtAuthenticated()).isFalse
        }

        @Test
        fun `should return false when no authentication`() {
            assertThat(securityUtils.isJwtAuthenticated()).isFalse
        }
    }

    @Nested
    inner class IsInternalRequest {

        @Test
        fun `should return true when has ROLE_INTERNAL`() {
            val auth = UsernamePasswordAuthenticationToken(
                "internal-service", null,
                listOf(SimpleGrantedAuthority(Role.INTERNAL.authority))
            )
            SecurityContextHolder.getContext().authentication = auth

            assertThat(securityUtils.isInternalRequest()).isTrue
        }

        @Test
        fun `should return false for regular PLAYER request`() {
            val jwt = buildJwt()
            setJwtAuth(jwt, authorities = listOf(Role.PLAYER.authority))

            assertThat(securityUtils.isInternalRequest()).isFalse
        }

        @Test
        fun `should return false when no authentication`() {
            assertThat(securityUtils.isInternalRequest()).isFalse
        }
    }
}