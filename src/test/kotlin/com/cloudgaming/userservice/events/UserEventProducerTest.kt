package com.cloudgaming.userservice.events

import com.cloudgaming.userservice.constants.KafkaTopics
import com.cloudgaming.userservice.container.PostgresTestContainerSingleton
import com.cloudgaming.userservice.container.RedisTestContainerSingleton
import com.cloudgaming.userservice.dto.BalanceLowEvent
import com.cloudgaming.userservice.dto.BalanceOperationAppliedEvent
import com.cloudgaming.userservice.dto.UserRegisteredEvent
import com.cloudgaming.userservice.dto.UserRoleChangedEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.math.BigDecimal
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = [
        KafkaTopics.USER_EVENTS,
        KafkaTopics.PAYMENT_TRANSACTIONS,
        KafkaTopics.SESSION_EVENTS
    ]
)
@Timeout(30)
class UserEventProducerTest {

    @Autowired
    private lateinit var eventProducer: UserEventProducer

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var eventCollector: EventCollector

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

    @BeforeEach
    fun setUp() {
        eventCollector.reset(userCount = 0, paymentCount = 0, sessionCount = 0)
    }

    @Test
    fun `should publish USER_REGISTERED event with correct JSON structure`() {
        eventCollector.reset(userCount = 1, paymentCount = 0, sessionCount = 0)

        val userId = UUID.randomUUID()
        eventProducer.publishUserRegistered(
            userId = userId,
            email = "alice@example.com",
            keycloakId = "kc-abc-123"
        )

        assertThat(eventCollector.userLatch.await(10, TimeUnit.SECONDS)).isTrue

        val record = eventCollector.receivedUserEvents[userId.toString()]
        assertThat(record).isNotNull

        val json = String(record!!.value())
        val event = objectMapper.readValue(json, UserRegisteredEvent::class.java)

        assertThat(event.eventId).isNotNull()
        assertThat(event.occurredAt).isNotNull()
        assertThat(event.eventType).isEqualTo("USER_REGISTERED")
        assertThat(event.userId).isEqualTo(userId)
        assertThat(event.email).isEqualTo("alice@example.com")
        assertThat(event.keycloakId).isEqualTo("kc-abc-123")
    }

    @Test
    fun `should publish USER_ROLE_CHANGED event`() {
        eventCollector.reset(userCount = 1, paymentCount = 0, sessionCount = 0)

        val targetUserId = UUID.randomUUID()
        val adminId = UUID.randomUUID()

        eventProducer.publishUserRoleChanged(
            targetUserId = targetUserId,
            performedByAdminId = adminId,
            role = "PREMIUM",
            action = "ADD"
        )

        assertThat(eventCollector.userLatch.await(10, TimeUnit.SECONDS)).isTrue

        val record = eventCollector.receivedUserEvents[targetUserId.toString()]
        val json = String(record!!.value())
        val event = objectMapper.readValue(json, UserRoleChangedEvent::class.java)

        assertThat(event.eventType).isEqualTo("USER_ROLE_CHANGED")
        assertThat(event.targetUserId).isEqualTo(targetUserId)
        assertThat(event.performedByAdminId).isEqualTo(adminId)
        assertThat(event.role).isEqualTo("PREMIUM")
        assertThat(event.action).isEqualTo("ADD")
    }

    @Test
    fun `should publish BALANCE_LOW event`() {
        eventCollector.reset(userCount = 1, paymentCount = 0, sessionCount = 0)

        val userId = UUID.randomUUID()
        eventProducer.publishBalanceLow(
            userId = userId,
            currentBalance = BigDecimal("50.00"),
            threshold = BigDecimal("100.00")
        )

        assertThat(eventCollector.userLatch.await(10, TimeUnit.SECONDS)).isTrue

        val record = eventCollector.receivedUserEvents[userId.toString()]
        val json = String(record!!.value())
        val event = objectMapper.readValue(json, BalanceLowEvent::class.java)

        assertThat(event.eventType).isEqualTo("BALANCE_LOW")
        assertThat(event.userId).isEqualTo(userId)
        assertThat(event.currentBalance).isEqualByComparingTo(BigDecimal("50.00"))
        assertThat(event.threshold).isEqualByComparingTo(BigDecimal("100.00"))
    }

    @Test
    fun `should route DEPOSIT to payment-transactions topic`() {
        eventCollector.reset(userCount = 0, paymentCount = 1, sessionCount = 0)

        val userId = UUID.randomUUID()
        eventProducer.publishBalanceOperationApplied(
            userId = userId,
            transactionId = UUID.randomUUID(),
            type = "DEPOSIT",
            amount = BigDecimal("500.00"),
            newBalance = BigDecimal("650.00")
        )

        assertThat(eventCollector.paymentLatch.await(10, TimeUnit.SECONDS)).isTrue
        assertThat(eventCollector.receivedUserEvents).isEmpty()
        assertThat(eventCollector.receivedSessionEvents).isEmpty()

        val record = eventCollector.receivedPaymentEvents[userId.toString()]
        assertThat(record).isNotNull

        val event = objectMapper.readValue(record!!.value(), BalanceOperationAppliedEvent::class.java)
        assertThat(event.type).isEqualTo("DEPOSIT")
        assertThat(event.amount).isEqualByComparingTo(BigDecimal("500.00"))
    }

    @Test
    fun `should route SESSION_DEBIT to session-events topic`() {
        eventCollector.reset(userCount = 0, paymentCount = 0, sessionCount = 1)

        val userId = UUID.randomUUID()
        eventProducer.publishBalanceOperationApplied(
            userId = userId,
            transactionId = UUID.randomUUID(),
            type = "SESSION_DEBIT",
            amount = BigDecimal("-100.00"),
            newBalance = BigDecimal("400.00")
        )

        assertThat(eventCollector.sessionLatch.await(10, TimeUnit.SECONDS)).isTrue
        assertThat(eventCollector.receivedUserEvents).isEmpty()
        assertThat(eventCollector.receivedPaymentEvents).isEmpty()

        val record = eventCollector.receivedSessionEvents[userId.toString()]
        assertThat(record).isNotNull

        val event = objectMapper.readValue(record!!.value(), BalanceOperationAppliedEvent::class.java)
        assertThat(event.type).isEqualTo("SESSION_DEBIT")
        assertThat(event.amount).isEqualByComparingTo(BigDecimal("-100.00"))
    }

    @Test
    fun `should route REFUND to session-events topic`() {
        eventCollector.reset(userCount = 0, paymentCount = 0, sessionCount = 1)

        val userId = UUID.randomUUID()
        eventProducer.publishBalanceOperationApplied(
            userId = userId,
            transactionId = UUID.randomUUID(),
            type = "REFUND",
            amount = BigDecimal("100.00"),
            newBalance = BigDecimal("500.00")
        )

        assertThat(eventCollector.sessionLatch.await(10, TimeUnit.SECONDS)).isTrue

        val record = eventCollector.receivedSessionEvents[userId.toString()]
        val event = objectMapper.readValue(record!!.value(), BalanceOperationAppliedEvent::class.java)
        assertThat(event.type).isEqualTo("REFUND")
    }

    @Test
    fun `should route BONUS to user-events topic`() {
        eventCollector.reset(userCount = 1, paymentCount = 0, sessionCount = 0)

        val userId = UUID.randomUUID()
        eventProducer.publishBalanceOperationApplied(
            userId = userId,
            transactionId = UUID.randomUUID(),
            type = "BONUS",
            amount = BigDecimal("50.00"),
            newBalance = BigDecimal("150.00")
        )

        assertThat(eventCollector.userLatch.await(10, TimeUnit.SECONDS)).isTrue
        assertThat(eventCollector.receivedPaymentEvents).isEmpty()
        assertThat(eventCollector.receivedSessionEvents).isEmpty()

        val record = eventCollector.receivedUserEvents[userId.toString()]
        val event = objectMapper.readValue(record!!.value(), BalanceOperationAppliedEvent::class.java)
        assertThat(event.type).isEqualTo("BONUS")
    }

    @Test
    fun `should route ADMIN_ADJUSTMENT to user-events topic`() {
        eventCollector.reset(userCount = 1, paymentCount = 0, sessionCount = 0)

        val userId = UUID.randomUUID()
        eventProducer.publishBalanceOperationApplied(
            userId = userId,
            transactionId = UUID.randomUUID(),
            type = "ADMIN_ADJUSTMENT",
            amount = BigDecimal("1000.00"),
            newBalance = BigDecimal("1100.00")
        )

        assertThat(eventCollector.userLatch.await(10, TimeUnit.SECONDS)).isTrue

        val record = eventCollector.receivedUserEvents[userId.toString()]
        val event = objectMapper.readValue(record!!.value(), BalanceOperationAppliedEvent::class.java)
        assertThat(event.type).isEqualTo("ADMIN_ADJUSTMENT")
    }

    @Test
    fun `should include event_type header in all events`() {
        eventCollector.reset(userCount = 1, paymentCount = 0, sessionCount = 0)

        eventProducer.publishUserRegistered(
            userId = UUID.randomUUID(),
            email = "test@test.com",
            keycloakId = "kc-1"
        )

        assertThat(eventCollector.userLatch.await(10, TimeUnit.SECONDS)).isTrue

        val record = eventCollector.receivedUserEvents.values.firstOrNull()
        assertThat(record).isNotNull
        assertThat(record!!.headers().lastHeader("event_type")).isNotNull
        val headerValue = String(record.headers().lastHeader("event_type")!!.value())
        assertThat(headerValue).isEqualTo("USER_REGISTERED")
    }

    @Test
    fun `should use userId as Kafka key for partitioning`() {
        eventCollector.reset(userCount = 1, paymentCount = 0, sessionCount = 0)

        val userId = UUID.randomUUID()
        eventProducer.publishUserRegistered(
            userId = userId,
            email = "test@test.com",
            keycloakId = "kc-1"
        )

        assertThat(eventCollector.userLatch.await(10, TimeUnit.SECONDS)).isTrue

        val record = eventCollector.receivedUserEvents[userId.toString()]
        assertThat(record).isNotNull
        assertThat(record!!.key()).isEqualTo(userId.toString())
    }

    @Test
    fun `should serialize Instant as ISO 8601 UTC`() {
        eventCollector.reset(userCount = 1, paymentCount = 0, sessionCount = 0)

        eventProducer.publishUserRegistered(
            userId = UUID.randomUUID(),
            email = "test@test.com",
            keycloakId = "kc-1"
        )

        assertThat(eventCollector.userLatch.await(10, TimeUnit.SECONDS)).isTrue

        val record = eventCollector.receivedUserEvents.values.first()
        val json = String(record.value())

        assertThat(json).contains("\"occurred_at\"")
        val node = objectMapper.readTree(json)
        val occurredAt = node.get("occurred_at").asText()
        val parsed = Instant.parse(occurredAt)
        assertThat(parsed).isNotNull
    }

    @TestConfiguration
    class KafkaTestListenerConfig {
        @Bean
        fun eventCollector(): EventCollector = EventCollector()
    }
}

class EventCollector {
    val receivedUserEvents = ConcurrentHashMap<String, ConsumerRecord<String, ByteArray>>()
    val receivedPaymentEvents = ConcurrentHashMap<String, ConsumerRecord<String, ByteArray>>()
    val receivedSessionEvents = ConcurrentHashMap<String, ConsumerRecord<String, ByteArray>>()

    @Volatile
    var userLatch = CountDownLatch(0)

    @Volatile
    var paymentLatch = CountDownLatch(0)

    @Volatile
    var sessionLatch = CountDownLatch(0)

    fun reset(userCount: Int, paymentCount: Int, sessionCount: Int) {
        receivedUserEvents.clear()
        receivedPaymentEvents.clear()
        receivedSessionEvents.clear()
        userLatch = CountDownLatch(userCount)
        paymentLatch = CountDownLatch(paymentCount)
        sessionLatch = CountDownLatch(sessionCount)
    }

    @KafkaListener(topics = [KafkaTopics.USER_EVENTS], groupId = "test-user-events")
    fun listenUserEvents(record: ConsumerRecord<String, ByteArray>) {
        receivedUserEvents[record.key()] = record
        userLatch.countDown()
    }

    @KafkaListener(topics = [KafkaTopics.PAYMENT_TRANSACTIONS], groupId = "test-payment")
    fun listenPaymentEvents(record: ConsumerRecord<String, ByteArray>) {
        receivedPaymentEvents[record.key()] = record
        paymentLatch.countDown()
    }

    @KafkaListener(topics = [KafkaTopics.SESSION_EVENTS], groupId = "test-session")
    fun listenSessionEvents(record: ConsumerRecord<String, ByteArray>) {
        receivedSessionEvents[record.key()] = record
        sessionLatch.countDown()
    }
}
