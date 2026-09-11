package com.cloudgaming.userservice.service

import com.cloudgaming.userservice.dto.BalanceOperationRequest
import com.cloudgaming.userservice.dto.BalanceOperationResponse
import com.cloudgaming.userservice.dto.CachedIdempotentResult
import com.cloudgaming.userservice.common.exception.ConcurrentBalanceOperationException
import com.cloudgaming.userservice.common.exception.IdempotencyConflictException
import com.cloudgaming.userservice.common.exception.InsufficientFundsException
import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.domain.BalanceTransaction
import com.cloudgaming.userservice.domain.TransactionType
import com.cloudgaming.userservice.domain.UserBalance
import com.cloudgaming.userservice.events.UserEventProducer
import com.cloudgaming.userservice.persistence.BalanceTransactionRepository
import com.cloudgaming.userservice.persistence.UserBalanceRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BalanceServiceUnitTest {

    @Mock
    private lateinit var userBalanceRepository: UserBalanceRepository

    @Mock
    private lateinit var balanceTransactionRepository: BalanceTransactionRepository

    @Mock
    private lateinit var redissonClient: RedissonClient

    @Mock
    private lateinit var stringRedisTemplate: StringRedisTemplate

    @Mock
    private lateinit var valueOperations: ValueOperations<String, String>

    @Mock
    private lateinit var eventProducer: UserEventProducer

    @Mock
    private lateinit var lock: RLock

    private val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
    }

    private lateinit var balanceService: BalanceService

    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        balanceService = BalanceService(
            userBalanceRepository,
            balanceTransactionRepository,
            redissonClient,
            stringRedisTemplate,
            eventProducer,
            objectMapper,
            BigDecimal("100.00")
        )

        whenever(stringRedisTemplate.opsForValue()).thenReturn(valueOperations)
        whenever(redissonClient.getLock(any<String>())).thenReturn(lock)
        whenever(lock.tryLock(any(), any(), any<TimeUnit>())).thenReturn(true)
        whenever(lock.isHeldByCurrentThread).thenReturn(true)
        whenever(valueOperations.get(any())).thenReturn(null)
    }

    private fun testBalance(amount: BigDecimal) = UserBalance(
        userId = userId,
        amount = amount,
        currency = "KZT",
        version = 0L
    )

    private fun request(
        type: TransactionType,
        amount: BigDecimal,
        idempotencyKey: String = UUID.randomUUID().toString(),
        sessionId: UUID? = null
    ) = BalanceOperationRequest(
        type = type,
        amount = amount,
        idempotencyKey = idempotencyKey,
        sessionId = sessionId
    )

    @Nested
    inner class HappyPath {

        @Test
        fun `should debit balance and store negative transaction amount for SESSION_DEBIT`() {
            val balance = testBalance(BigDecimal("500.00"))
            whenever(userBalanceRepository.findByUserId(userId)).thenReturn(balance)
            whenever(userBalanceRepository.save(any())).thenAnswer { it.getArgument(0) }
            whenever(balanceTransactionRepository.save(any())).thenAnswer { it.getArgument(0) }

            val response = balanceService.applyOperation(
                userId,
                request(type = TransactionType.SESSION_DEBIT, amount = BigDecimal("100.00"), sessionId = UUID.randomUUID())
            )

            assertThat(response.newBalance).isEqualByComparingTo(BigDecimal("400.00"))
            assertThat(balance.amount).isEqualByComparingTo(BigDecimal("400.00"))

            val captor = org.mockito.kotlin.argumentCaptor<BalanceTransaction>()
            verify(balanceTransactionRepository).save(captor.capture())
            assertThat(captor.firstValue.amount).isEqualByComparingTo(BigDecimal("-100.00"))
            assertThat(captor.firstValue.type).isEqualTo(TransactionType.SESSION_DEBIT)
        }

        @Test
        fun `should credit balance and store positive transaction amount for DEPOSIT`() {
            val balance = testBalance(BigDecimal.ZERO)
            whenever(userBalanceRepository.findByUserId(userId)).thenReturn(balance)
            whenever(userBalanceRepository.save(any())).thenAnswer { it.getArgument(0) }
            whenever(balanceTransactionRepository.save(any())).thenAnswer { it.getArgument(0) }

            val response = balanceService.applyOperation(
                userId,
                request(type = TransactionType.DEPOSIT, amount = BigDecimal("500.00"))
            )

            assertThat(response.newBalance).isEqualByComparingTo(BigDecimal("500.00"))
            assertThat(balance.amount).isEqualByComparingTo(BigDecimal("500.00"))

            val captor = org.mockito.kotlin.argumentCaptor<BalanceTransaction>()
            verify(balanceTransactionRepository).save(captor.capture())
            assertThat(captor.firstValue.amount).isEqualByComparingTo(BigDecimal("500.00"))
            assertThat(captor.firstValue.type).isEqualTo(TransactionType.DEPOSIT)
        }
    }

    @Nested
    inner class Idempotency {

        @Test
        fun `should return cached response without touching repositories on replay`() {
            val idempotencyKey = "replay-key"
            val cachedResponse = BalanceOperationResponse(
                transactionId = UUID.randomUUID(),
                newBalance = BigDecimal("400.00"),
                currency = "KZT",
                processedAt = Instant.parse("2024-01-01T00:00:00Z")
            )
            val cached = CachedIdempotentResult(
                type = TransactionType.SESSION_DEBIT,
                amount = BigDecimal("100.00"),
                response = cachedResponse
            )
            whenever(valueOperations.get(any())).thenReturn(objectMapper.writeValueAsString(cached))

            val response = balanceService.applyOperation(
                userId,
                request(type = TransactionType.SESSION_DEBIT, amount = BigDecimal("100.00"), idempotencyKey = idempotencyKey)
            )

            assertThat(response).isEqualTo(cachedResponse)
            verify(userBalanceRepository, never()).findByUserId(any())
            verify(balanceTransactionRepository, never()).save(any())
        }

        @Test
        fun `should throw IdempotencyConflictException when same key used with different amount`() {
            val idempotencyKey = "conflict-key"
            val cachedResponse = BalanceOperationResponse(
                transactionId = UUID.randomUUID(),
                newBalance = BigDecimal("400.00"),
                currency = "KZT",
                processedAt = Instant.parse("2024-01-01T00:00:00Z")
            )
            val cached = CachedIdempotentResult(
                type = TransactionType.SESSION_DEBIT,
                amount = BigDecimal("100.00"),
                response = cachedResponse
            )
            whenever(valueOperations.get(any())).thenReturn(objectMapper.writeValueAsString(cached))

            assertThatThrownBy {
                balanceService.applyOperation(
                    userId,
                    request(
                        type = TransactionType.SESSION_DEBIT,
                        amount = BigDecimal("200.00"),
                        idempotencyKey = idempotencyKey
                    )
                )
            }.isInstanceOf(IdempotencyConflictException::class.java)

            verify(userBalanceRepository, never()).findByUserId(any())
        }
    }

    @Nested
    inner class Locking {

        @Test
        fun `should throw ConcurrentBalanceOperationException when lock is not acquired`() {
            whenever(lock.tryLock(any(), any(), any<TimeUnit>())).thenReturn(false)

            assertThatThrownBy {
                balanceService.applyOperation(
                    userId,
                    request(type = TransactionType.DEPOSIT, amount = BigDecimal("100.00"))
                )
            }.isInstanceOf(ConcurrentBalanceOperationException::class.java)

            verify(userBalanceRepository, never()).findByUserId(any())
        }
    }

    @Nested
    inner class BusinessRules {

        @Test
        fun `should throw InsufficientFundsException when SESSION_DEBIT exceeds balance`() {
            whenever(userBalanceRepository.findByUserId(userId)).thenReturn(testBalance(BigDecimal("100.00")))

            assertThatThrownBy {
                balanceService.applyOperation(
                    userId,
                    request(type = TransactionType.SESSION_DEBIT, amount = BigDecimal("500.00"), sessionId = UUID.randomUUID())
                )
            }.isInstanceOf(InsufficientFundsException::class.java)

            verify(balanceTransactionRepository, never()).save(any())
            verify(valueOperations).setIfAbsent(any(), eq("pending"), any<Duration>())
            verify(stringRedisTemplate).delete(any<String>())
        }

        @Test
        fun `should throw UserNotFoundException when balance not found for user`() {
            whenever(userBalanceRepository.findByUserId(userId)).thenReturn(null)

            assertThatThrownBy {
                balanceService.applyOperation(
                    userId,
                    request(type = TransactionType.DEPOSIT, amount = BigDecimal("100.00"))
                )
            }.isInstanceOf(UserNotFoundException::class.java)
        }

        @Test
        fun `should reject non-positive amount at DTO validation level`() {
            val validator = jakarta.validation.Validation.buildDefaultValidatorFactory().validator
            val invalidRequest = BalanceOperationRequest(
                type = TransactionType.DEPOSIT,
                amount = BigDecimal("-50.00"),
                idempotencyKey = "neg-amount-key",
                paymentId = UUID.randomUUID()
            )

            val violations = validator.validate(invalidRequest)

            assertThat(violations).isNotEmpty
        }
    }

    @Nested
    inner class Retries {

        @Test
        fun `should clean up idempotency pending marker when save conflicts with ObjectOptimisticLockingFailureException`() {
            whenever(userBalanceRepository.findByUserId(userId)).thenReturn(testBalance(BigDecimal("500.00")))
            whenever(userBalanceRepository.save(any())).thenThrow(
                ObjectOptimisticLockingFailureException(UserBalance::class.java, userId)
            )

            assertThatThrownBy {
                balanceService.applyOperation(
                    userId,
                    request(type = TransactionType.DEPOSIT, amount = BigDecimal("50.00"))
                )
            }.isInstanceOf(ObjectOptimisticLockingFailureException::class.java)

            verify(stringRedisTemplate).delete(any<String>())
        }
    }
}
