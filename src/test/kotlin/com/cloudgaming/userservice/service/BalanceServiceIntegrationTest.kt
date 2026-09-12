package com.cloudgaming.userservice.service

import com.cloudgaming.userservice.dto.BalanceOperationRequest
import com.cloudgaming.userservice.common.exception.ConcurrentBalanceOperationException
import com.cloudgaming.userservice.common.exception.IdempotencyConflictException
import com.cloudgaming.userservice.common.exception.InsufficientFundsException
import com.cloudgaming.userservice.constants.KafkaTopics
import com.cloudgaming.userservice.container.KafkaTestContainerSingleton
import com.cloudgaming.userservice.container.PostgresTestContainerSingleton
import com.cloudgaming.userservice.container.RedisTestContainerSingleton
import com.cloudgaming.userservice.domain.User
import com.cloudgaming.userservice.domain.TransactionType
import com.cloudgaming.userservice.domain.UserBalance
import com.cloudgaming.userservice.persistence.BalanceTransactionRepository
import com.cloudgaming.userservice.persistence.UserBalanceRepository
import com.cloudgaming.userservice.persistence.UserRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID
import jakarta.persistence.EntityManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = [KafkaTopics.USER_EVENTS, KafkaTopics.PAYMENT_TRANSACTIONS, KafkaTopics.SESSION_EVENTS]
)
@Timeout(60)
@DisplayName("BalanceService integration tests (PostgreSQL + Redis + Redisson + Kafka)")
@Import(BalanceServiceIntegrationTest.BalanceEventListenerTestConfig::class)
class BalanceServiceIntegrationTest {

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
            registry.add("REDIS_HOST") { redis.host }
            registry.add("REDIS_PORT") { redis.getMappedPort(6379) }
            registry.add("REDIS_PASSWORD") { RedisTestContainerSingleton.PASSWORD }
            registry.add("app.kafka.auto-create-topics") { "false" }
            registry.add("app.balance.low-threshold") { "100.00" }
        }
    }

    @Autowired
    private lateinit var balanceService: BalanceService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userBalanceRepository: UserBalanceRepository

    @Autowired
    private lateinit var balanceTransactionRepository: BalanceTransactionRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var eventCollector: BalanceEventCollector

    private lateinit var userId: UUID
    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM balance_transactions")
        jdbcTemplate.execute("DELETE FROM user_balances")
        jdbcTemplate.execute("DELETE FROM users")
        eventCollector.reset(0)

        user = userRepository.save(
            User(
                keycloakId = "kc-${UUID.randomUUID()}",
                email = "balance-${UUID.randomUUID()}@example.com",
                displayName = "Balance Test User"
            )
        )
        userId = user.id
    }

    private fun seedBalance(amount: BigDecimal) {
        userBalanceRepository.save(
            UserBalance(userId = userId, user = user, amount = amount, currency = "KZT")
        )
    }

    private fun depositRequest(
        amount: BigDecimal,
        idempotencyKey: String = UUID.randomUUID().toString()
    ) = BalanceOperationRequest(
        type = TransactionType.DEPOSIT,
        amount = amount,
        idempotencyKey = idempotencyKey,
        paymentId = UUID.randomUUID()
    )

    private fun sessionDebitRequest(
        amount: BigDecimal,
        idempotencyKey: String = UUID.randomUUID().toString(),
        sessionId: UUID = UUID.randomUUID()
    ) = BalanceOperationRequest(
        type = TransactionType.SESSION_DEBIT,
        amount = amount,
        idempotencyKey = idempotencyKey,
        sessionId = sessionId
    )

    @Nested
    @DisplayName("Concurrency: distributed lock + optimistic locking")
    inner class Concurrency {

        @Test
        @DisplayName("2 parallel SESSION_DEBIT of 100 from balance of 1000 should leave balance at 800 with 2 transactions")
        fun `should serialize parallel session debits and keep balance consistent`() {
            seedBalance(BigDecimal("1000.00"))
            val pool = Executors.newFixedThreadPool(2)
            val startLatch = CountDownLatch(1)
            val doneLatch = CountDownLatch(2)

            repeat(2) {
                pool.submit {
                    startLatch.await()
                    try {
                        balanceService.applyOperation(userId, sessionDebitRequest(BigDecimal("100.00")))
                    } finally {
                        doneLatch.countDown()
                    }
                }
            }
            startLatch.countDown()
            assertThat(doneLatch.await(20, TimeUnit.SECONDS)).isTrue
            pool.shutdown()

            val balance = userBalanceRepository.findByUserId(userId)!!
            assertThat(balance.amount).isEqualByComparingTo(BigDecimal("800.00"))
            assertThat(balanceTransactionRepository.countByUserId(userId)).isEqualTo(2)
        }
    }

    @Nested
    @DisplayName("Idempotency")
    inner class Idempotency {

        @Test
        @DisplayName("Repeated request with same idempotency_key returns same transactionId and does not change balance twice")
        fun `should return cached result on replay without duplicating side effects`() {
            seedBalance(BigDecimal("500.00"))
            val idempotencyKey = UUID.randomUUID().toString()
            val request = depositRequest(BigDecimal("50.00"), idempotencyKey)

            val first = balanceService.applyOperation(userId, request)
            val second = balanceService.applyOperation(userId, request)

            assertThat(second.transactionId).isEqualTo(first.transactionId)
            assertThat(userBalanceRepository.findByUserId(userId)!!.amount).isEqualByComparingTo(BigDecimal("550.00"))
            assertThat(balanceTransactionRepository.countByUserId(userId)).isEqualTo(1)
        }

        @Test
        @DisplayName("Parallel requests with same idempotency_key but different amount: exactly one succeeds")
        fun `should let exactly one operation succeed and reject the conflicting duplicate`() {
            seedBalance(BigDecimal("1000.00"))
            val idempotencyKey = UUID.randomUUID().toString()
            val pool = Executors.newFixedThreadPool(2)
            val startLatch = CountDownLatch(1)
            val doneLatch = CountDownLatch(2)
            val successes = ConcurrentHashMap.newKeySet<UUID>()
            val conflicts = ConcurrentHashMap.newKeySet<Int>()
            val unexpected = java.util.Collections.synchronizedList(mutableListOf<Throwable>())

            listOf(BigDecimal("10.00"), BigDecimal("20.00")).forEachIndexed { index, amount ->
                pool.submit {
                    try {
                        startLatch.await()
                        try {
                            val response = balanceService.applyOperation(
                                userId,
                                depositRequest(amount, idempotencyKey)
                            )
                            successes.add(response.transactionId)
                        } catch (ex: IdempotencyConflictException) {
                            conflicts.add(index)
                        }
                    } catch (ex: Throwable) {
                        unexpected.add(ex)
                    } finally {
                        doneLatch.countDown()
                    }
                }
            }
            startLatch.countDown()
            assertThat(doneLatch.await(20, TimeUnit.SECONDS)).isTrue
            pool.shutdown()

            assertThat(unexpected).isEmpty()
            assertThat(successes).hasSize(1)
            assertThat(conflicts).hasSize(1)
            assertThat(balanceTransactionRepository.countByUserId(userId)).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("Business rules")
    inner class BusinessRules {

        @Test
        @DisplayName("SESSION_DEBIT above balance leaves no side effects and no idempotency cache entry")
        fun `should fail insufficient funds without persisting anything or caching`() {
            seedBalance(BigDecimal("100.00"))
            val idempotencyKey = UUID.randomUUID().toString()

            assertThatThrownBy {
                balanceService.applyOperation(userId, sessionDebitRequest(BigDecimal("500.00"), idempotencyKey))
            }.isInstanceOf(InsufficientFundsException::class.java)

            assertThat(userBalanceRepository.findByUserId(userId)!!.amount).isEqualByComparingTo(BigDecimal("100.00"))
            assertThat(balanceTransactionRepository.countByUserId(userId)).isEqualTo(0)

            val retried = balanceService.applyOperation(userId, sessionDebitRequest(BigDecimal("50.00"), idempotencyKey))
            assertThat(retried.newBalance).isEqualByComparingTo(BigDecimal("50.00"))
        }
    }

    @Nested
    @DisplayName("Kafka event publishing")
    inner class KafkaEvents {

        @Test
        @DisplayName("DEPOSIT publishes BALANCE_OPERATION_APPLIED to payment-transactions")
        fun `should publish deposit event to payment-transactions topic`() {
            seedBalance(BigDecimal("0.00"))
            eventCollector.reset(1)

            balanceService.applyOperation(userId, depositRequest(BigDecimal("500.00")))

            assertThat(eventCollector.paymentLatch.await(15, TimeUnit.SECONDS)).isTrue
            assertThat(eventCollector.receivedPaymentEvents).containsKey(userId.toString())
        }

        @Test
        @DisplayName("SESSION_DEBIT publishes BALANCE_OPERATION_APPLIED to session-events")
        fun `should publish session debit event to session-events topic`() {
            seedBalance(BigDecimal("500.00"))
            eventCollector.reset(1)

            balanceService.applyOperation(userId, sessionDebitRequest(BigDecimal("100.00")))

            assertThat(eventCollector.sessionLatch.await(15, TimeUnit.SECONDS)).isTrue
            assertThat(eventCollector.receivedSessionEvents).containsKey(userId.toString())
        }

        @Test
        @DisplayName("Balance below threshold also publishes BALANCE_LOW to user-events")
        fun `should publish balance low event when resulting balance is under threshold`() {
            seedBalance(BigDecimal("150.00"))
            eventCollector.reset(1, expectLowBalance = true)

            balanceService.applyOperation(userId, sessionDebitRequest(BigDecimal("100.00")))

            assertThat(eventCollector.userLatch.await(15, TimeUnit.SECONDS)).isTrue
            assertThat(eventCollector.receivedUserEvents).containsKey(userId.toString())
        }
    }

    @Nested
    @DisplayName("Optimistic locking retry")
    inner class OptimisticLockRetry {

        @MockitoSpyBean
        private lateinit var userBalanceRepository: UserBalanceRepository

        @Autowired
        private lateinit var entityManager: EntityManager

        @Test
        @DisplayName("@Retry re-executes applyOperation and succeeds after a single ObjectOptimisticLockingFailureException")
        fun `should retry and succeed after a single optimistic locking failure on save`() {
            seedBalance(BigDecimal("500.00"))
            val callCount = AtomicInteger(0)
            doAnswer { invocation ->
                if (callCount.getAndIncrement() == 0) {
                    throw ObjectOptimisticLockingFailureException(UserBalance::class.java, userId)
                }
                val entity = invocation.getArgument<UserBalance>(0)
                // Bypass Hibernate's own dirty-checking flush (which would otherwise race with this
                // manual update and re-throw the optimistic lock failure) by writing directly via JDBC
                // and detaching the managed entity so it is not auto-flushed at commit.
                jdbcTemplate.update(
                    "UPDATE user_balances SET amount = ?, last_operation_at = ?, version = version + 1 WHERE user_id = ?",
                    entity.amount,
                    java.sql.Timestamp.from(java.time.Instant.now()),
                    userId
                )
                entityManager.detach(entity)
                entity
            }.whenever(userBalanceRepository).save(any())

            val response = balanceService.applyOperation(userId, depositRequest(BigDecimal("50.00")))

            assertThat(response.newBalance).isEqualByComparingTo(BigDecimal("550.00"))
            assertThat(callCount.get()).isEqualTo(2)
        }
    }

    @TestConfiguration
    class BalanceEventListenerTestConfig {
        @Bean
        fun balanceEventCollector(): BalanceEventCollector = BalanceEventCollector()
    }
}

class BalanceEventCollector {
    val receivedPaymentEvents = ConcurrentHashMap<String, ConsumerRecord<String, ByteArray>>()
    val receivedSessionEvents = ConcurrentHashMap<String, ConsumerRecord<String, ByteArray>>()
    val receivedUserEvents = ConcurrentHashMap<String, ConsumerRecord<String, ByteArray>>()

    @Volatile
    var paymentLatch = CountDownLatch(0)

    @Volatile
    var sessionLatch = CountDownLatch(0)

    @Volatile
    var userLatch = CountDownLatch(0)

    @Volatile
    private var expectLowBalance = false

    fun reset(expectedCount: Int, expectLowBalance: Boolean = false) {
        receivedPaymentEvents.clear()
        receivedSessionEvents.clear()
        receivedUserEvents.clear()
        this.expectLowBalance = expectLowBalance
        paymentLatch = CountDownLatch(expectedCount)
        sessionLatch = CountDownLatch(expectedCount)
        userLatch = CountDownLatch(if (expectLowBalance) expectedCount else 0)
    }

    @KafkaListener(topics = [KafkaTopics.PAYMENT_TRANSACTIONS], groupId = "test-balance-payment-events")
    fun listenPayment(record: ConsumerRecord<String, ByteArray>) {
        receivedPaymentEvents[record.key()] = record
        paymentLatch.countDown()
    }

    @KafkaListener(topics = [KafkaTopics.SESSION_EVENTS], groupId = "test-balance-session-events")
    fun listenSession(record: ConsumerRecord<String, ByteArray>) {
        receivedSessionEvents[record.key()] = record
        sessionLatch.countDown()
    }

    @KafkaListener(topics = [KafkaTopics.USER_EVENTS], groupId = "test-balance-user-events")
    fun listenUser(record: ConsumerRecord<String, ByteArray>) {
        if (!expectLowBalance || !String(record.value()).contains("BALANCE_LOW")) {
            return
        }
        receivedUserEvents[record.key()] = record
        userLatch.countDown()
    }
}
