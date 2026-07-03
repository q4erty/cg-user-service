package com.cloudgaming.userservice.common.security

import com.cloudgaming.userservice.constants.JwtClaim
import com.cloudgaming.userservice.constants.Role
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

    fun getCurrentEmail(): String? = getCurrentJwt().claims[JwtClaim.EMAIL.claimName] as? String

    fun getCurrentUsername(): String? =
        getCurrentJwt().claims[JwtClaim.PREFERRED_USERNAME.claimName] as? String

    fun hasRole(role: Role): Boolean {
        val auth = SecurityContextHolder.getContext().authentication ?: return false
        return auth.authorities.any { it.authority == role.authority }
    }

    fun isJwtAuthenticated(): Boolean {
        val auth = SecurityContextHolder.getContext().authentication ?: return false
        return auth is JwtAuthenticationToken
    }

    fun isInternalRequest(): Boolean {
        val auth = SecurityContextHolder.getContext().authentication ?: return false
        return auth.authorities.any { it.authority == Role.INTERNAL.authority }
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