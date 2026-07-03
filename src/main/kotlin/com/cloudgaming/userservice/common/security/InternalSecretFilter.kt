package com.cloudgaming.userservice.common.security

import com.cloudgaming.userservice.constants.ApiPaths
import com.cloudgaming.userservice.constants.HeaderNames
import com.cloudgaming.userservice.constants.Role
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

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val uri = request.requestURI

        if (!uri.startsWith(ApiPaths.INTERNAL_PREFIX)) {
            chain.doFilter(request, response)
            return
        }

        val providedSecret = request.getHeader(HeaderNames.INTERNAL_SECRET)

        if (providedSecret == null || providedSecret != expectedSecret) {
            logger.warn("Unauthorized access to internal endpoint: $uri (missing or invalid ${HeaderNames.INTERNAL_SECRET})")
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write(
                """{"error":"UNAUTHORIZED","message":"Invalid or missing ${HeaderNames.INTERNAL_SECRET} header"}"""
            )
            return
        }

        val auth = UsernamePasswordAuthenticationToken(
            "internal-service",
            null,
            listOf(SimpleGrantedAuthority(Role.INTERNAL.authority))
        )
        SecurityContextHolder.getContext().authentication = auth

        chain.doFilter(request, response)
    }
}
