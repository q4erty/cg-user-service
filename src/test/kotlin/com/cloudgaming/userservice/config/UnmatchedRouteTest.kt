package com.cloudgaming.userservice.config

import com.cloudgaming.userservice.common.security.InternalSecretFilter
import com.cloudgaming.userservice.container.KafkaTestContainerSingleton
import com.cloudgaming.userservice.container.PostgresTestContainerSingleton
import com.cloudgaming.userservice.container.RedisTestContainerSingleton
import com.cloudgaming.userservice.events.UserEventProducer
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class UnmatchedRouteTest {

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
            registry.add("app.kafka.auto-create-topics") { "false" }
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

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

    @Test
    fun `unmatched route should return 404 handled by GlobalExceptionHandler`() {
        mockMvc.perform(get("/api/v1/nonexistent"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.error").value("NOT_FOUND"))
            .andExpect(jsonPath("$.path").value("/api/v1/nonexistent"))
    }
}