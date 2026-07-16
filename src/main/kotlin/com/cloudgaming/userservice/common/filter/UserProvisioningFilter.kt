package com.cloudgaming.userservice.common.filter

import com.cloudgaming.userservice.constants.JwtClaim
import com.cloudgaming.userservice.service.UserProvisioningService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class UserProvisioningFilter(
    private val provisioningService: UserProvisioningService
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val authentication = SecurityContextHolder.getContext().authentication

        if (authentication is JwtAuthenticationToken) {
            val jwt = authentication.token

            try {
                val keycloakId = jwt.subject
                if (keycloakId != null) {
                    val email = jwt.claims[JwtClaim.EMAIL.claimName] as? String
                    val displayName = jwt.claims[JwtClaim.PREFERRED_USERNAME.claimName] as? String

                    provisioningService.validateUserExists(keycloakId, email, displayName)
                }
            } catch (e: Exception) {
                logger.error(
                    "Provisioning failed for keycloakId={}: {}",
                    jwt.subject, e.message, e
                )
            }
        }

        chain.doFilter(request, response)
    }
}
