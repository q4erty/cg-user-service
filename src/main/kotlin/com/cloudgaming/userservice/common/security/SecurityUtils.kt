package com.cloudgaming.userservice.common.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

@Component
class SecurityUtils {

    fun getCurrentKeycloakId(): String {
        val jwt = getCurrentJwt()
        return jwt.subject ?: throw IllegalStateException("JWT subject (keycloak_id) is null")
    }

    fun getCurrentEmail(): String? = getCurrentJwt().claims["email"] as? String

    fun getCurrentUsername(): String? =
        getCurrentJwt().claims["preferred_username"] as? String

    fun hasRole(role: String): Boolean {
        val auth = SecurityContextHolder.getContext().authentication ?: return false
        return auth.authorities.any { it.authority == "ROLE_${role.uppercase(java.util.Locale.ROOT)}" }
    }

    fun isJwtAuthenticated(): Boolean {
        val auth = SecurityContextHolder.getContext().authentication ?: return false
        return auth is JwtAuthenticationToken
    }

    fun isInternalRequest(): Boolean {
        val auth = SecurityContextHolder.getContext().authentication ?: return false
        return auth.authorities.any { it.authority == "ROLE_INTERNAL" }
    }

    private fun getCurrentJwt(): Jwt {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw IllegalStateException("No authentication in SecurityContext")

        return when (auth) {
            is JwtAuthenticationToken -> auth.token
            else -> throw IllegalStateException(
                "Expected JwtAuthenticationToken but got ${auth::class.simpleName}"
            )
        }
    }
}