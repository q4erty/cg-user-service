package com.cloudgaming.userservice.service

import com.cloudgaming.userservice.common.exception.TransactionNotFoundException
import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.domain.BalanceTransaction
import com.cloudgaming.userservice.domain.TransactionType
import com.cloudgaming.userservice.domain.UserBalance
import com.cloudgaming.userservice.persistence.BalanceTransactionRepository
import com.cloudgaming.userservice.persistence.UserBalanceRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class BalanceQueryServiceTest {

    @Mock
    private lateinit var userBalanceRepository: UserBalanceRepository

    @Mock
    private lateinit var balanceTransactionRepository: BalanceTransactionRepository

    @InjectMocks
    private lateinit var balanceQueryService: BalanceQueryService

    private lateinit var userId: UUID

    @BeforeEach
    fun setUp() {
        userId = UUID.randomUUID()
    }

    private fun testBalance(amount: BigDecimal = BigDecimal("150.00")) = UserBalance(
        userId = userId,
        amount = amount,
        currency = "KZT",
        lastOperationAt = Instant.parse("2026-06-19T12:00:00Z")
    )

    private fun testTransaction(
        id: UUID = UUID.randomUUID(),
        type: TransactionType = TransactionType.DEPOSIT,
        amount: BigDecimal = BigDecimal("50.00")
    ) = BalanceTransaction(
        id = id,
        userId = userId,
        amount = amount,
        type = type,
        idempotencyKey = "key-$id",
        createdAt = Instant.parse("2026-06-19T12:00:00Z")
    )

    @Test
    fun `getBalance should return dto when balance exists`() {
        whenever(userBalanceRepository.findByUserId(userId)).thenReturn(testBalance())

        val result = balanceQueryService.getBalance(userId)

        assertThat(result.amount).isEqualByComparingTo(BigDecimal("150.00"))
        assertThat(result.currency).isEqualTo("KZT")
    }

    @Test
    fun `getBalance should throw UserNotFoundException when balance missing`() {
        whenever(userBalanceRepository.findByUserId(userId)).thenReturn(null)

        assertThatThrownBy { balanceQueryService.getBalance(userId) }
            .isInstanceOf(UserNotFoundException::class.java)
    }

    @Test
    fun `getTransactionHistory should map page with default sort`() {
        val transaction = testTransaction()
        whenever(balanceTransactionRepository.findByUserId(eq(userId), any()))
            .thenReturn(PageImpl(listOf(transaction)))

        val result = balanceQueryService.getTransactionHistory(userId, page = 0, size = 20, sort = "createdAt,desc")

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].id).isEqualTo(transaction.id)
        assertThat(result.content[0].type).isEqualTo("DEPOSIT")
    }

    @Test
    fun `getTransactionHistory should build pageable with requested sort direction`() {
        whenever(balanceTransactionRepository.findByUserId(eq(userId), any()))
            .thenReturn(PageImpl(emptyList()))

        balanceQueryService.getTransactionHistory(userId, page = 1, size = 10, sort = "createdAt,asc")

        val expected: Pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.ASC, "createdAt"))
        org.mockito.kotlin.verify(balanceTransactionRepository).findByUserId(userId, expected)
    }

    @Test
    fun `getTransactionHistory should reject negative page`() {
        assertThatThrownBy {
            balanceQueryService.getTransactionHistory(userId, page = -1, size = 20, sort = "createdAt,desc")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `getTransactionHistory should reject size above max`() {
        assertThatThrownBy {
            balanceQueryService.getTransactionHistory(userId, page = 0, size = 101, sort = "createdAt,desc")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `getTransactionHistory should reject size below min`() {
        assertThatThrownBy {
            balanceQueryService.getTransactionHistory(userId, page = 0, size = 0, sort = "createdAt,desc")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `getTransactionHistory should reject unsupported sort property`() {
        assertThatThrownBy {
            balanceQueryService.getTransactionHistory(userId, page = 0, size = 20, sort = "amount,desc")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `getTransactionHistory should reject invalid sort direction`() {
        assertThatThrownBy {
            balanceQueryService.getTransactionHistory(userId, page = 0, size = 20, sort = "createdAt,sideways")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `getTransactionById should return dto when found`() {
        val transaction = testTransaction()
        whenever(balanceTransactionRepository.findByIdAndUserId(transaction.id, userId))
            .thenReturn(transaction)

        val result = balanceQueryService.getTransactionById(userId, transaction.id)

        assertThat(result.id).isEqualTo(transaction.id)
        assertThat(result.amount).isEqualByComparingTo(transaction.amount)
    }

    @Test
    fun `getTransactionById should throw TransactionNotFoundException when missing`() {
        val transactionId = UUID.randomUUID()
        whenever(balanceTransactionRepository.findByIdAndUserId(transactionId, userId))
            .thenReturn(null)

        assertThatThrownBy { balanceQueryService.getTransactionById(userId, transactionId) }
            .isInstanceOf(TransactionNotFoundException::class.java)
    }

    @Test
    fun `getTransactionById should not leak another users transaction`() {
        val otherUserId = UUID.randomUUID()
        val transactionId = UUID.randomUUID()
        whenever(balanceTransactionRepository.findByIdAndUserId(transactionId, otherUserId))
            .thenReturn(null)

        assertThatThrownBy { balanceQueryService.getTransactionById(otherUserId, transactionId) }
            .isInstanceOf(TransactionNotFoundException::class.java)
    }
}
