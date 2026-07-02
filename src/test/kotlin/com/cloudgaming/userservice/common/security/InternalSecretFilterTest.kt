package com.cloudgaming.userservice.common.security

import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class InternalSecretFilterTest {

    private val expectedSecret = "test-secret-12345"
    private val filter = InternalSecretFilter(expectedSecret)
    private val chain: FilterChain = mock()

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Nested
    inner class NonInternalPaths {

        @Test
        fun `should skip filter for non-internal path`() {
            val request = MockHttpServletRequest("GET", "/api/v1/users/me")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            verify(chain).doFilter(request, response)
            assertThat(SecurityContextHolder.getContext().authentication).isNull()
        }

        @Test
        fun `should skip filter for actuator path`() {
            val request = MockHttpServletRequest("GET", "/actuator/health")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            verify(chain).doFilter(request, response)
        }

        @Test
        fun `should skip filter for swagger-ui`() {
            val request = MockHttpServletRequest("GET", "/swagger-ui/index.html")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            verify(chain).doFilter(request, response)
        }
    }

    @Nested
    inner class InternalPathsWithValidSecret {

        @Test
        fun `should authenticate and continue when secret is valid`() {
            val request = MockHttpServletRequest("POST", "/api/internal/users/123/balance/operations")
            request.addHeader(InternalSecretFilter.SECRET_HEADER, expectedSecret)
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            verify(chain).doFilter(request, response)

            val auth = SecurityContextHolder.getContext().authentication
            assertThat(auth).isNotNull
            assertThat(auth!!.name).isEqualTo("internal-service")
            assertThat(auth.authorities).anyMatch { it.authority == InternalSecretFilter.ROLE_INTERNAL }
        }

        @Test
        fun `should authenticate for GET on internal path`() {
            val request = MockHttpServletRequest("GET", "/api/internal/users/123/balance")
            request.addHeader(InternalSecretFilter.SECRET_HEADER, expectedSecret)
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            assertThat(SecurityContextHolder.getContext().authentication).isNotNull
            verify(chain).doFilter(request, response)
        }
    }

    @Nested
    inner class InternalPathsWithInvalidSecret {

        @Test
        fun `should return 401 when secret header is missing`() {
            val request = MockHttpServletRequest("POST", "/api/internal/users/123/balance/operations")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            assertThat(response.status).isEqualTo(401)
            assertThat(response.contentType).contains("application/json")
            assertThat(response.contentAsString).contains("UNAUTHORIZED")
            verifyNoInteractions(chain)
        }

        @Test
        fun `should return 401 when secret is wrong`() {
            val request = MockHttpServletRequest("POST", "/api/internal/users/123/balance/operations")
            request.addHeader(InternalSecretFilter.SECRET_HEADER, "wrong-secret")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            assertThat(response.status).isEqualTo(401)
            verifyNoInteractions(chain)
        }

        @Test
        fun `should return 401 when secret is empty string`() {
            val request = MockHttpServletRequest("POST", "/api/internal/users/123/balance/operations")
            request.addHeader(InternalSecretFilter.SECRET_HEADER, "")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            assertThat(response.status).isEqualTo(401)
            verifyNoInteractions(chain)
        }

        @Test
        fun `should not set authentication when secret is invalid`() {
            val request = MockHttpServletRequest("POST", "/api/internal/users/123/balance/operations")
            request.addHeader(InternalSecretFilter.SECRET_HEADER, "wrong-secret")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            assertThat(SecurityContextHolder.getContext().authentication).isNull()
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `should not interfere with already-authenticated context`() {
            val request = MockHttpServletRequest("POST", "/api/internal/users/123/balance/operations")
            request.addHeader(InternalSecretFilter.SECRET_HEADER, expectedSecret)
            request.addHeader("Authorization", "Bearer some-jwt-token")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            val auth = SecurityContextHolder.getContext().authentication
            assertThat(auth).isNotNull
            assertThat(auth!!.name).isEqualTo("internal-service")
        }

        @Test
        fun `should handle path that starts with 'api-internal' but is not exact prefix`() {
            val request = MockHttpServletRequest("GET", "/api/internalization/foo")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            verify(chain).doFilter(request, response)
            assertThat(SecurityContextHolder.getContext().authentication).isNull()
        }
    }
}
