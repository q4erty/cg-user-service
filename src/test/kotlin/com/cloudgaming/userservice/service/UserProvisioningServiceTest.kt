package com.cloudgaming.userservice.service

import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.constants.RedisKey
import com.cloudgaming.userservice.container.KafkaTestContainerSingleton
import com.cloudgaming.userservice.container.PostgresTestContainerSingleton
import com.cloudgaming.userservice.container.RedisTestContainerSingleton
import com.cloudgaming.userservice.domain.User
import com.cloudgaming.userservice.events.UserEventProducer
import com.cloudgaming.userservice.persistence.UserBalanceRepository
import com.cloudgaming.userservice.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.transaction.TestTransaction
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("test")
class UserProvisioningServiceTest {

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
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userBalanceRepository: UserBalanceRepository

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    private lateinit var provisioningService: UserProvisioningService

    @MockitoBean
    private lateinit var eventProducer: UserEventProducer

    @BeforeEach
    fun setUp() {
        userRepository.deleteAll()
        redisTemplate.connectionFactory?.connection?.commands()?.flushAll()
    }

    @Nested
    open inner class EnsureUserExists {

        @Test
        @Transactional
        open fun `should create new user with zero balance on first call`() {
            val keycloakId = "kc-${UUID.randomUUID()}"

            val user = provisioningService.ensureUserExists(
                keycloakId = keycloakId,
                email = "new@example.com",
                displayName = "New User"
            )

            assertThat(user.id).isNotNull()
            assertThat(user.keycloakId).isEqualTo(keycloakId)
            assertThat(user.email).isEqualTo("new@example.com")
            assertThat(user.displayName).isEqualTo("New User")
            assertThat(user.lastLoginAt).isNotNull()

            val balance = userBalanceRepository.findByUserId(user.id)
            assertThat(balance).isNotNull
            assertThat(balance!!.amount).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(balance.currency).isEqualTo("KZT")
        }

        @Test
        fun `should publish USER_REGISTERED event on first provisioning`() {
            val keycloakId = "kc-${UUID.randomUUID()}"

            provisioningService.ensureUserExists(keycloakId, "test@example.com", "Test")

            verify(eventProducer).publishUserRegistered(any(), any(), any())
        }

        @Test
        fun `should cache user existence in Redis after first provisioning`() {
            val keycloakId = "kc-${UUID.randomUUID()}"

            provisioningService.ensureUserExists(keycloakId, "test@example.com", null)

            val cached = redisTemplate.hasKey("${RedisKey.EXISTS_PREFIX}$keycloakId")
            assertThat(cached).isTrue
        }

        @Test
        fun `should cache keycloak_id to UUID mapping in Redis`() {
            val keycloakId = "kc-${UUID.randomUUID()}"

            val user = provisioningService.ensureUserExists(keycloakId, "test@example.com", null)

            val cachedId = redisTemplate.opsForValue().get("${RedisKey.ID_MAPPING_PREFIX}$keycloakId")
            assertThat(cachedId).isEqualTo(user.id.toString())
        }

        @Test
        @Transactional
        open fun `should not create duplicate user on second call (Redis cache fast path)`() {
            val keycloakId = "kc-${UUID.randomUUID()}"

            val user1 = provisioningService.ensureUserExists(keycloakId, "test@example.com", null)
            TestTransaction.flagForCommit()
            TestTransaction.end()
            TestTransaction.start()

            val user2 = provisioningService.ensureUserExists(keycloakId, "test@example.com", null)

            assertThat(user2.id).isEqualTo(user1.id)
            assertThat(userRepository.count()).isEqualTo(1L)
        }

        @Test
        fun `should not publish USER_REGISTERED on subsequent calls`() {
            val keycloakId = "kc-${UUID.randomUUID()}"

            provisioningService.ensureUserExists(keycloakId, "test@example.com", null)
            clearInvocations(eventProducer)
            provisioningService.ensureUserExists(keycloakId, "test@example.com", null)

            verify(eventProducer, never()).publishUserRegistered(any(), any(), any())
        }

        @Test
        fun `should sync email when it changes in Keycloak`() {
            val keycloakId = "kc-${UUID.randomUUID()}"

            provisioningService.ensureUserExists(keycloakId, "old@example.com", null)

            redisTemplate.delete("${RedisKey.EXISTS_PREFIX}$keycloakId")
            redisTemplate.delete("${RedisKey.ID_MAPPING_PREFIX}$keycloakId")

            val updated = provisioningService.ensureUserExists(keycloakId, "new@example.com", null)

            assertThat(updated.email).isEqualTo("new@example.com")
        }

        @Test
        fun `should use fallback email when JWT has none`() {
            val keycloakId = "kc-${UUID.randomUUID()}"

            val user = provisioningService.ensureUserExists(keycloakId, null, null)

            assertThat(user.email).isEqualTo("$keycloakId@keycloak.local")
        }

        @Test
        fun `should handle parallel provisioning without duplicates`() {
            val keycloakId = "kc-parallel-${UUID.randomUUID()}"
            val threadCount = 10
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)
            val results = mutableListOf<User>()
            val exceptions = mutableListOf<Exception>()

            repeat(threadCount) {
                executor.submit {
                    try {
                        val user = provisioningService.ensureUserExists(keycloakId, "parallel@test.com", null)
                        synchronized(results) { results.add(user) }
                    } catch (e: Exception) {
                        synchronized(exceptions) { exceptions.add(e) }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            assertThat(results.size + exceptions.size).isEqualTo(threadCount)
            assertThat(results.map { it.id }.toSet()).hasSize(1)
            assertThat(userRepository.count()).isEqualTo(1L)
        }
    }

    @Nested
    inner class GetInternalUserId {

        @Test
        fun `should return UUID when user exists in cache`() {
            val keycloakId = "kc-${UUID.randomUUID()}"
            val user = provisioningService.ensureUserExists(keycloakId, "test@example.com", null)

            val result = provisioningService.getInternalUserId(keycloakId)

            assertThat(result).isEqualTo(user.id)
        }

        @Test
        fun `should return UUID from DB when cache missed`() {
            val keycloakId = "kc-${UUID.randomUUID()}"
            val user = provisioningService.ensureUserExists(keycloakId, "test@example.com", null)

            redisTemplate.delete("${RedisKey.ID_MAPPING_PREFIX}$keycloakId")
            redisTemplate.delete("${RedisKey.EXISTS_PREFIX}$keycloakId")

            val result = provisioningService.getInternalUserId(keycloakId)

            assertThat(result).isEqualTo(user.id)
        }

        @Test
        fun `should return null when user does not exist`() {
            val result = provisioningService.getInternalUserId("non-existent-keycloak-id")
            assertThat(result).isNull()
        }
    }

    @Nested
    inner class RequireInternalUserId {

        @Test
        fun `should return UUID when user exists`() {
            val keycloakId = "kc-${UUID.randomUUID()}"
            val user = provisioningService.ensureUserExists(keycloakId, "test@example.com", null)

            val result = provisioningService.requireInternalUserId(keycloakId)

            assertThat(result).isEqualTo(user.id)
        }

        @Test
        fun `should throw UserNotFoundException when user does not exist`() {
            assertThrows<UserNotFoundException> {
                provisioningService.requireInternalUserId("non-existent")
            }
        }
    }

    @Nested
    inner class GetByKeycloakId {

        @Test
        fun `should return user when exists`() {
            val keycloakId = "kc-${UUID.randomUUID()}"
            val created = provisioningService.ensureUserExists(keycloakId, "test@example.com", "Test")

            val found = provisioningService.getByKeycloakId(keycloakId)

            assertThat(found.id).isEqualTo(created.id)
            assertThat(found.email).isEqualTo("test@example.com")
        }

        @Test
        fun `should throw UserNotFoundException when not exists`() {
            assertThrows<UserNotFoundException> {
                provisioningService.getByKeycloakId("non-existent")
            }
        }
    }
}