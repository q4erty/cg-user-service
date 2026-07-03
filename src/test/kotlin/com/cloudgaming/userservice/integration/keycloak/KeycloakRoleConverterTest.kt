package com.cloudgaming.userservice.integration.keycloak

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class KeycloakRoleConverterTest {

    private val converter = KeycloakRoleConverter()

    private fun buildJwt(claims: Map<String, Any>): Jwt {
        return Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .claims { it.putAll(claims) }
            .build()
    }

    @Nested
    inner class HappyPath {

        @Test
        fun `should extract PLAYER role and add ROLE_ prefix`() {
            val jwt = buildJwt(
                mapOf("realm_access" to mapOf("roles" to listOf("PLAYER")))
            )

            val authorities = converter.convert(jwt)

            assertThat(authorities).hasSize(1)
            assertThat(authorities).anyMatch { it.authority == "ROLE_PLAYER" }
        }

        @Test
        fun `should extract PREMIUM role`() {
            val jwt = buildJwt(
                mapOf("realm_access" to mapOf("roles" to listOf("PREMIUM")))
            )

            val authorities = converter.convert(jwt)

            assertThat(authorities).anyMatch { it.authority == "ROLE_PREMIUM" }
        }

        @Test
        fun `should extract ADMIN role`() {
            val jwt = buildJwt(
                mapOf("realm_access" to mapOf("roles" to listOf("ADMIN")))
            )

            val authorities = converter.convert(jwt)

            assertThat(authorities).anyMatch { it.authority == "ROLE_ADMIN" }
        }

        @Test
        fun `should extract all three allowed roles`() {
            val jwt = buildJwt(
                mapOf("realm_access" to mapOf("roles" to listOf("PLAYER", "PREMIUM", "ADMIN")))
            )

            val authorities = converter.convert(jwt)

            assertThat(authorities).hasSize(3)
            assertThat(authorities.map { it.authority }).containsExactlyInAnyOrder(
                "ROLE_PLAYER", "ROLE_PREMIUM", "ROLE_ADMIN"
            )
        }
    }

    @Nested
    inner class Filtering {

        @Test
        fun `should filter out offline_access role`() {
            val jwt = buildJwt(
                mapOf(
                    "realm_access" to mapOf(
                        "roles" to listOf("PLAYER", "offline_access")
                    )
                )
            )

            val authorities = converter.convert(jwt)

            assertThat(authorities).hasSize(1)
            assertThat(authorities).noneMatch { it.authority == "ROLE_OFFLINE_ACCESS" }
        }

        @Test
        fun `should filter out default-roles-cloud-gaming`() {
            val jwt = buildJwt(
                mapOf(
                    "realm_access" to mapOf(
                        "roles" to listOf("PLAYER", "default-roles-cloud-gaming")
                    )
                )
            )

            val authorities = converter.convert(jwt)

            assertThat(authorities).hasSize(1)
            assertThat(authorities).noneMatch { it.authority == "ROLE_DEFAULT-ROLES-CLOUD-GAMING" }
        }

        @Test
        fun `should filter out uma_authorization`() {
            val jwt = buildJwt(
                mapOf(
                    "realm_access" to mapOf(
                        "roles" to listOf("ADMIN", "uma_authorization")
                    )
                )
            )

            val authorities = converter.convert(jwt)

            assertThat(authorities).hasSize(1)
            assertThat(authorities).noneMatch { it.authority == "ROLE_UMA_AUTHORIZATION" }
        }

        @Test
        fun `should filter out unknown roles`() {
            val jwt = buildJwt(
                mapOf(
                    "realm_access" to mapOf(
                        "roles" to listOf("PLAYER", "SOME_WEIRD_ROLE", "ANOTHER_UNKNOWN")
                    )
                )
            )

            val authorities = converter.convert(jwt)

            assertThat(authorities).hasSize(1)
            assertThat(authorities.first().authority).isEqualTo("ROLE_PLAYER")
        }
    }

    @Nested
    inner class CaseNormalization {

        @Test
        fun `should convert lowercase roles to uppercase`() {
            val jwt = buildJwt(
                mapOf("realm_access" to mapOf("roles" to listOf("player", "admin")))
            )

            val authorities = converter.convert(jwt)

            assertThat(authorities).anyMatch { it.authority == "ROLE_PLAYER" }
            assertThat(authorities).anyMatch { it.authority == "ROLE_ADMIN" }
        }

        @Test
        fun `should handle mixed case roles`() {
            val jwt = buildJwt(
                mapOf("realm_access" to mapOf("roles" to listOf("PlAyEr", "PrEmIuM")))
            )

            val authorities = converter.convert(jwt)

            assertThat(authorities).hasSize(2)
            assertThat(authorities.map { it.authority }).contains("ROLE_PLAYER", "ROLE_PREMIUM")
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `should return empty list when realm_access claim is missing`() {
            val jwt = buildJwt(mapOf("sub" to "user-123"))

            val authorities = converter.convert(jwt)

            assertThat(authorities).isEmpty()
        }

        @Test
        fun `should return empty list when realm_access has no roles field`() {
            val jwt = buildJwt(
                mapOf("realm_access" to mapOf("other_field" to "value"))
            )

            val authorities = converter.convert(jwt)

            assertThat(authorities).isEmpty()
        }

        @Test
        fun `should return empty list when roles array is empty`() {
            val jwt = buildJwt(
                mapOf("realm_access" to mapOf("roles" to emptyList<String>()))
            )

            val authorities = converter.convert(jwt)

            assertThat(authorities).isEmpty()
        }

        @Test
        fun `should return empty list when realm_access is not a Map`() {
            val jwt = buildJwt(
                mapOf("realm_access" to "wrong_type")
            )

            val authorities = converter.convert(jwt)

            assertThat(authorities).isEmpty()
        }

        @Test
        fun `should return empty list when roles is not a List`() {
            val jwt = buildJwt(
                mapOf("realm_access" to mapOf("roles" to "PLAYER"))
            )

            val authorities = converter.convert(jwt)

            assertThat(authorities).isEmpty()
        }
    }
}