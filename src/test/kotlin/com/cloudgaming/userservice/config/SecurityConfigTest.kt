package com.cloudgaming.userservice.config

import com.cloudgaming.userservice.common.security.InternalSecretFilter
import com.cloudgaming.userservice.container.KafkaTestContainerSingleton
import com.cloudgaming.userservice.container.PostgresTestContainerSingleton
import com.cloudgaming.userservice.container.RedisTestContainerSingleton
import com.cloudgaming.userservice.dto.UserEventProducer
import com.cloudgaming.userservice.integration.keycloak.KeycloakRoleConverter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    companion object {
        private val postgres = PostgresTestContainerSingleton.instance
        private val redis = RedisTestContainerSingleton.instance
        private val kafka = KafkaTestContainerSingleton.instance

        @JvmStatic
        @DynamicPropertySource
        fun overrideProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("spring.data.redis.password") { RedisTestContainerSingleton.PASSWORD }
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var keycloakRoleConverter: KeycloakRoleConverter

    @MockitoBean
    private lateinit var internalSecretFilter: InternalSecretFilter

    @MockitoBean
    private lateinit var userEventProducer: UserEventProducer

    @BeforeEach
    fun setUp() {
        doAnswer { invocation ->
            val request = invocation.getArgument<HttpServletRequest>(0)
            val response = invocation.getArgument<HttpServletResponse>(1)
            val chain = invocation.getArgument<FilterChain>(2)
            chain.doFilter(request, response)
        }.`when`(internalSecretFilter).doFilter(any(), any(), any())
    }


    @Nested
    inner class PublicEndpoints {
        @Test
        fun `actuator health should be accessible without auth`() {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk)
        }

        @Test
        fun `actuator info should be accessible without auth`() {
            mockMvc.perform(get("/actuator/info")).andExpect(status().isOk)
        }

        @Test
        fun `swagger-ui should be accessible without auth`() {
            mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk)
        }

        @Test
        fun `api-docs should be accessible without auth`() {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk)
        }
    }

    @Nested
    inner class ProtectedEndpointsWithoutAuth {
        @Test
        fun `users me without auth should return 401`() {
            mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized)
        }

        @Test
        fun `admin users without auth should return 401`() {
            mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized)
        }

        @Test
        fun `internal endpoints without auth should return 401`() {
            mockMvc.perform(post("/api/internal/users/123/balance/operations"))
                .andExpect(status().isUnauthorized)
        }
    }
}