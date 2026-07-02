package com.cloudgaming.userservice.common.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class InternalSecretFilter(
    @Value("\${app.security.internal-secret}") private val expectedSecret: String
) : OncePerRequestFilter() {

    companion object {
        const val INTERNAL_PATH_PREFIX = "/api/internal/"
        const val SECRET_HEADER = "X-Internal-Secret"
        const val ROLE_INTERNAL = "ROLE_INTERNAL"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val uri = request.requestURI

        if (!uri.startsWith(INTERNAL_PATH_PREFIX)) {
            chain.doFilter(request, response)
            return
        }

        val providedSecret = request.getHeader(SECRET_HEADER)

        if (providedSecret == null || providedSecret != expectedSecret) {
            logger.warn("Unauthorized access to internal endpoint: $uri (missing or invalid $SECRET_HEADER)")
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write(
                """{"error":"UNAUTHORIZED","message":"Invalid or missing $SECRET_HEADER header"}"""
            )
            return
        }

        val auth = UsernamePasswordAuthenticationToken(
            "internal-service",
            null,
            listOf(SimpleGrantedAuthority(ROLE_INTERNAL))
        )
        SecurityContextHolder.getContext().authentication = auth

        chain.doFilter(request, response)
    }
}