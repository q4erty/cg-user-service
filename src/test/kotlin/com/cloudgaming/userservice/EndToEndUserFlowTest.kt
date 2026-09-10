package com.cloudgaming.userservice

import com.cloudgaming.userservice.constants.JwtClaim
import com.cloudgaming.userservice.constants.KafkaTopics
import com.cloudgaming.userservice.container.PostgresTestContainerSingleton
import com.cloudgaming.userservice.container.RedisTestContainerSingleton
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = [KafkaTopics.USER_EVENTS, KafkaTopics.PAYMENT_TRANSACTIONS, KafkaTopics.SESSION_EVENTS]
)
@Timeout(60)
@DisplayName("End-to-end: JWT -> JIT provisioning -> profile flow")
@Import(EndToEndUserFlowTest.KafkaListenerTestConfig::class)
class EndToEndUserFlowTest {

    companion object {
        private val postgres = PostgresTestContainerSingleton.instance
        private val redis = RedisTestContainerSingleton.instance

        @JvmStatic
        @DynamicPropertySource
        fun overrideProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("spring.data.redis.password") { RedisTestContainerSingleton.PASSWORD }
            registry.add("app.kafka.auto-create-topics") { "false" }
        }
    }

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var userEventCollector: UserEventCollector

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM user_balances")
        jdbcTemplate.execute("DELETE FROM users")
        userEventCollector.reset(0)
    }

    private fun meUrl() = "http://localhost:$port/api/v1/users/me"

    private fun buildJwt(
        keycloakId: String,
        email: String? = "user@example.com",
        username: String = "user_name",
        roles: List<String> = listOf("PLAYER")
    ): Jwt {
        return Jwt.withTokenValue("test-token-$keycloakId")
            .header("alg", "RS256")
            .subject(keycloakId)
            .claims { claims ->
                if (email != null) {
                    claims[JwtClaim.EMAIL.claimName] = email
                }
                claims[JwtClaim.PREFERRED_USERNAME.claimName] = username
                claims[JwtClaim.REALM_ACCESS.claimName] = mapOf(JwtClaim.ROLES.claimName to roles)
            }
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
    }

    private fun mockJwtDecoder(jwt: Jwt) {
        whenever(jwtDecoder.decode(any())).thenReturn(jwt)
    }

    private fun authHeaders(token: String): HttpHeaders =
        HttpHeaders().apply {
            setBearerAuth(token)
            contentType = MediaType.APPLICATION_JSON
        }

    private fun getMe(token: String): ResponseEntity<String> =
        restTemplate.exchange(
            meUrl(),
            HttpMethod.GET,
            HttpEntity<String>(authHeaders(token)),
            String::class.java
        )

    private fun patchMe(token: String, body: String): ResponseEntity<String> =
        restTemplate.exchange(
            meUrl(),
            HttpMethod.PATCH,
            HttpEntity(body, authHeaders(token)),
            String::class.java
        )

    private fun parseJson(response: ResponseEntity<String>): JsonNode = objectMapper.readTree(response.body)

    private fun countUsersByKeycloakId(keycloakId: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE keycloak_id = ?",
            Int::class.java,
            keycloakId
        ) ?: 0

    private fun countBalancesByKeycloakId(keycloakId: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_balances b JOIN users u ON u.id = b.user_id WHERE u.keycloak_id = ?",
            Int::class.java,
            keycloakId
        ) ?: 0

    private fun getBalance(keycloakId: String): Pair<BigDecimal, String> =
        jdbcTemplate.queryForObject(
            "SELECT b.amount, b.currency FROM user_balances b JOIN users u ON u.id = b.user_id WHERE u.keycloak_id = ?",
            { rs, _ -> rs.getBigDecimal("amount") to rs.getString("currency") },
            keycloakId
        )!!

    private fun getDisplayName(keycloakId: String): String? =
        jdbcTemplate.queryForObject(
            "SELECT display_name FROM users WHERE keycloak_id = ?",
            String::class.java,
            keycloakId
        )

    @TestConfiguration
    class KafkaListenerTestConfig {
        @Bean
        fun userEventCollector(): UserEventCollector = UserEventCollector()
    }

    @Nested
    @DisplayName("Provisioning")
    inner class Provisioning {

        @Test
        @DisplayName("10 parallel requests with same keycloak_id should create only 1 user")
        fun `10 parallel requests with same keycloak_id should create only 1 user`() {
            val keycloakId = "kc-parallel-${UUID.randomUUID()}"
            val token = "test-token-$keycloakId"
            mockJwtDecoder(buildJwt(keycloakId))

            val threadCount = 10
            val executor = Executors.newFixedThreadPool(threadCount)
            val responses = ConcurrentLinkedQueue<ResponseEntity<String>>()
            val latch = CountDownLatch(threadCount)

            try {
                val futures = (1..threadCount).map {
                    CompletableFuture.supplyAsync({
                        try {
                            responses.add(getMe(token))
                        } finally {
                            latch.countDown()
                        }
                    }, executor)
                }
                latch.await(30, TimeUnit.SECONDS)
                CompletableFuture.allOf(*futures.toTypedArray()).get(30, TimeUnit.SECONDS)
            } finally {
                executor.shutdown()
            }

            assertThat(responses)
                .withFailMessage("Expected $threadCount responses but got ${responses.size}: $responses")
                .hasSize(threadCount)
            assertThat(responses.all { it.statusCode == HttpStatus.OK })
                .withFailMessage("Not all responses returned 200 OK: $responses")
                .isTrue()

            assertThat(countUsersByKeycloakId(keycloakId))
                .withFailMessage("Expected exactly 1 user row for keycloakId=$keycloakId")
                .isEqualTo(1)
            assertThat(countBalancesByKeycloakId(keycloakId))
                .withFailMessage("Expected exactly 1 user_balances row for keycloakId=$keycloakId")
                .isEqualTo(1)

            val (amount, _) = getBalance(keycloakId)
            assertThat(amount).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @Test
        @DisplayName("USER_REGISTERED event should be published on first login")
        fun `USER_REGISTERED event should be published on first login`() {
            userEventCollector.reset(1)
            val keycloakId = "kc-event-${UUID.randomUUID()}"
            val token = "test-token-$keycloakId"
            val email = "new-$keycloakId@test.com"
            mockJwtDecoder(buildJwt(keycloakId, email = email))

            val response = getMe(token)
            assertThat(response.statusCode)
                .withFailMessage("Response: status=${response.statusCode}, body=${response.body}")
                .isEqualTo(HttpStatus.OK)

            assertThat(userEventCollector.latch.await(10, TimeUnit.SECONDS))
                .withFailMessage("Timed out waiting for USER_REGISTERED event on topic ${KafkaTopics.USER_EVENTS}")
                .isTrue()

            assertThat(userEventCollector.receivedEvents)
                .withFailMessage("Expected exactly 1 USER_REGISTERED event, got: ${userEventCollector.receivedEvents}")
                .hasSize(1)

            val record = userEventCollector.receivedEvents.values.first()
            val event = objectMapper.readTree(String(record.value()))

            assertThat(event.get("event_type").asText()).isEqualTo("USER_REGISTERED")
            assertThat(event.get("user_id").asText()).isNotBlank()
            assertThat(event.get("email").asText()).isEqualTo(email)
            assertThat(event.get("keycloak_id").asText()).isEqualTo(keycloakId)
        }

        @Test
        @DisplayName("GET me should return profile with zero balance after provisioning")
        fun `GET me should return profile with zero balance after provisioning`() {
            val keycloakId = "kc-profile-${UUID.randomUUID()}"
            val token = "test-token-$keycloakId"
            val email = "profile-$keycloakId@test.com"
            mockJwtDecoder(buildJwt(keycloakId, email = email))

            val response = getMe(token)

            assertThat(response.statusCode)
                .withFailMessage("Response: status=${response.statusCode}, body=${response.body}")
                .isEqualTo(HttpStatus.OK)

            val json = parseJson(response)
            assertThat(json.get("id").asText()).isNotBlank()
            assertThat(json.get("email").asText()).isEqualTo(email)
            assertThat(json.get("balance").asDouble()).isEqualTo(0.0)
            assertThat(json.get("currency").asText()).isEqualTo("KZT")
            assertThat(json.get("created_at").asText()).isNotBlank()
            assertThat(json.get("last_login_at").asText()).isNotBlank()

            val (amount, currency) = getBalance(keycloakId)
            assertThat(amount).isEqualByComparingTo(BigDecimal("0.00"))
            assertThat(currency).isEqualTo("KZT")
        }
    }

    @Nested
    @DisplayName("Profile update")
    inner class ProfileUpdate {

        @Test
        @DisplayName("PATCH me should update displayName and GET me should reflect change")
        fun `PATCH me should update displayName and GET me should reflect change`() {
            val keycloakId = "kc-update-${UUID.randomUUID()}"
            val token = "test-token-$keycloakId"
            mockJwtDecoder(buildJwt(keycloakId))

            val firstGet = getMe(token)
            assertThat(firstGet.statusCode)
                .withFailMessage("Response: ${firstGet.statusCode}, body=${firstGet.body}")
                .isEqualTo(HttpStatus.OK)

            val patchBody = """{"display_name": "Alice Updated", "avatar_url": "https://example.com/new.png"}"""
            val patchResponse = patchMe(token, patchBody)

            assertThat(patchResponse.statusCode)
                .withFailMessage("Response: ${patchResponse.statusCode}, body=${patchResponse.body}")
                .isEqualTo(HttpStatus.OK)

            val patchJson = parseJson(patchResponse)
            assertThat(patchJson.get("display_name").asText()).isEqualTo("Alice Updated")
            assertThat(patchJson.get("avatar_url").asText()).isEqualTo("https://example.com/new.png")

            val secondGet = getMe(token)
            val secondJson = parseJson(secondGet)
            assertThat(secondJson.get("display_name").asText())
                .withFailMessage("Cache was not evicted after PATCH, GET returned stale data: $secondJson")
                .isEqualTo("Alice Updated")
            assertThat(secondJson.get("avatar_url").asText()).isEqualTo("https://example.com/new.png")
        }

        @Test
        @DisplayName("PATCH me with invalid URL should return 400")
        fun `PATCH me with invalid URL should return 400`() {
            val keycloakId = "kc-invalid-${UUID.randomUUID()}"
            val token = "test-token-$keycloakId"
            mockJwtDecoder(buildJwt(keycloakId))
            getMe(token)

            val patchBody = """{"display_name": "Alice", "avatar_url": "not-a-url"}"""
            val response = patchMe(token, patchBody)

            assertThat(response.statusCode)
                .withFailMessage("Response: ${response.statusCode}, body=${response.body}")
                .isEqualTo(HttpStatus.BAD_REQUEST)

            val json = parseJson(response)
            assertThat(json.get("error").asText()).isEqualTo("VALIDATION_ERROR")
            assertThat(json.has("details")).isTrue()

            assertThat(getDisplayName(keycloakId)).isEqualTo("user_name")
        }

        @Test
        @DisplayName("PATCH me without PLAYER role should return 403")
        fun `PATCH me without PLAYER role should return 403`() {
            val keycloakId = "kc-badrole-${UUID.randomUUID()}"
            val token = "test-token-$keycloakId"
            mockJwtDecoder(buildJwt(keycloakId, roles = listOf("SOME_OTHER_ROLE")))

            val patchBody = """{"display_name": "Alice", "avatar_url": null}"""
            val response = patchMe(token, patchBody)

            assertThat(response.statusCode)
                .withFailMessage("Response: ${response.statusCode}, body=${response.body}")
                .isEqualTo(HttpStatus.FORBIDDEN)

            val json = parseJson(response)
            assertThat(json.get("error").asText()).isEqualTo("ACCESS_DENIED")
        }
    }
}

class UserEventCollector {
    val receivedEvents = ConcurrentHashMap<String, ConsumerRecord<String, ByteArray>>()

    @Volatile
    var latch = CountDownLatch(0)

    fun reset(expectedCount: Int) {
        receivedEvents.clear()
        latch = CountDownLatch(expectedCount)
    }

    @KafkaListener(topics = [KafkaTopics.USER_EVENTS], groupId = "test-e2e-user-events")
    fun listen(record: ConsumerRecord<String, ByteArray>) {
        receivedEvents[record.key()] = record
        latch.countDown()
    }
}
