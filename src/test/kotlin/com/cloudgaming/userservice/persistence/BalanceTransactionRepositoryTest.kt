package com.cloudgaming.userservice.persistence

import com.cloudgaming.userservice.domain.BalanceTransaction
import com.cloudgaming.userservice.domain.TransactionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class BalanceTransactionRepositoryTest : AbstractJpaTest() {

    @Test
    fun `should save and find transaction by idempotency_key`() {
        val user = createTestUser(keycloakId = "k1-${UUID.randomUUID()}", initialBalance = BigDecimal("500.00"))

        val txn = BalanceTransaction(
            userId = user.id,
            amount = BigDecimal("-100.00"),
            type = TransactionType.SESSION_DEBIT,
            sessionId = UUID.randomUUID(),
            idempotencyKey = "session-${UUID.randomUUID()}:debit",
            description = "Cloud gaming session 60 min"
        )
        balanceTransactionRepository.save(txn)

        val found = balanceTransactionRepository.findByIdempotencyKey(txn.idempotencyKey)

        assertThat(found).isNotNull
        assertThat(found!!.amount).isEqualByComparingTo(BigDecimal("-100.00"))
        assertThat(found.type).isEqualTo(TransactionType.SESSION_DEBIT)
        assertThat(found.description).contains("60 min")
    }

    @Test
    fun `existsByIdempotencyKey should return correct result`() {
        val user = createTestUser(keycloakId = "k1-${UUID.randomUUID()}")
        val key = "test-key-${UUID.randomUUID()}"

        balanceTransactionRepository.save(
            BalanceTransaction(
                userId = user.id,
                amount = BigDecimal("50.00"),
                type = TransactionType.BONUS,
                idempotencyKey = key
            )
        )

        assertThat(balanceTransactionRepository.existsByIdempotencyKey(key)).isTrue
        assertThat(balanceTransactionRepository.existsByIdempotencyKey("non-existent")).isFalse
    }

    @Test
    fun `should return transactions ordered by createdAt DESC`() {
        val user = createTestUser(keycloakId = "k1-${UUID.randomUUID()}")
        val now = Instant.now()

        val txn1 = balanceTransactionRepository.save(BalanceTransaction(
            userId = user.id, amount = BigDecimal("100.00"),
            type = TransactionType.DEPOSIT, idempotencyKey = "k1",
            createdAt = now.minusSeconds(20)
        ))
        val txn2 = balanceTransactionRepository.save(BalanceTransaction(
            userId = user.id, amount = BigDecimal("-50.00"),
            type = TransactionType.SESSION_DEBIT, idempotencyKey = "k2",
            createdAt = now.minusSeconds(10)
        ))
        val txn3 = balanceTransactionRepository.save(BalanceTransaction(
            userId = user.id, amount = BigDecimal("10.00"),
            type = TransactionType.BONUS, idempotencyKey = "k3",
            createdAt = now
        ))

        val page = balanceTransactionRepository.findByUserIdOrderByCreatedAtDesc(
            user.id, PageRequest.of(0, 10)
        )

        assertThat(page.content).hasSize(3)
        assertThat(page.content[0].id).isEqualTo(txn3.id)
        assertThat(page.content[1].id).isEqualTo(txn2.id)
        assertThat(page.content[2].id).isEqualTo(txn1.id)
    }

    @Test
    fun `should paginate transactions correctly`() {
        val user = createTestUser(keycloakId = "k1-${UUID.randomUUID()}")
        val now = Instant.now()

        repeat(5) { i ->
            balanceTransactionRepository.save(BalanceTransaction(
                userId = user.id,
                amount = BigDecimal("${i * 10}"),
                type = TransactionType.DEPOSIT,
                idempotencyKey = "page-k-$i",
                createdAt = now.minusSeconds((5 - i).toLong())
            ))
        }

        val page0 = balanceTransactionRepository.findByUserIdOrderByCreatedAtDesc(
            user.id, PageRequest.of(0, 2)
        )
        val page1 = balanceTransactionRepository.findByUserIdOrderByCreatedAtDesc(
            user.id, PageRequest.of(1, 2)
        )

        assertThat(page0.content).hasSize(2)
        assertThat(page1.content).hasSize(2)
        assertThat(page0.totalElements).isEqualTo(5)
        assertThat(page0.totalPages).isEqualTo(3)
    }

    @Test
    fun `should find transactions by session_id`() {
        val user = createTestUser(keycloakId = "k1-${UUID.randomUUID()}")
        val sessionId = UUID.randomUUID()

        balanceTransactionRepository.save(
            BalanceTransaction(
                userId = user.id, amount = BigDecimal("-100.00"),
                type = TransactionType.SESSION_DEBIT, sessionId = sessionId,
                idempotencyKey = "s1-debit"
            )
        )
        balanceTransactionRepository.save(
            BalanceTransaction(
                userId = user.id, amount = BigDecimal("100.00"),
                type = TransactionType.REFUND, sessionId = sessionId,
                idempotencyKey = "s1-refund"
            )
        )
        balanceTransactionRepository.save(
            BalanceTransaction(
                userId = user.id, amount = BigDecimal("-50.00"),
                type = TransactionType.SESSION_DEBIT, sessionId = UUID.randomUUID(),
                idempotencyKey = "s2-debit"
            )
        )

        val txns = balanceTransactionRepository.findBySessionId(sessionId)

        assertThat(txns).hasSize(2)
        assertThat(txns).allMatch { it.sessionId == sessionId }
    }

    @Test
    fun `should find deposits by payment_id`() {
        val user = createTestUser(keycloakId = "k1-${UUID.randomUUID()}")
        val paymentId = UUID.randomUUID()

        balanceTransactionRepository.save(
            BalanceTransaction(
                userId = user.id, amount = BigDecimal("500.00"),
                type = TransactionType.DEPOSIT, paymentId = paymentId,
                idempotencyKey = "p1"
            )
        )
        balanceTransactionRepository.save(
            BalanceTransaction(
                userId = user.id, amount = BigDecimal("-50.00"),
                type = TransactionType.SESSION_DEBIT,
                paymentId = paymentId,
                idempotencyKey = "p2"
            )
        )

        val deposits = balanceTransactionRepository.findByPaymentIdAndType(
            paymentId, TransactionType.DEPOSIT
        )

        assertThat(deposits).hasSize(1)
        assertThat(deposits[0].amount).isEqualByComparingTo(BigDecimal("500.00"))
    }

    @Test
    fun `should sum amount by user and period`() {
        val user = createTestUser(keycloakId = "k1-${UUID.randomUUID()}")

        val now = Instant.now()
        val hourAgo = now.minusSeconds(3600)
        val twoHoursAgo = now.minusSeconds(7200)
        val dayAhead = now.plusSeconds(86400)

        balanceTransactionRepository.save(
            BalanceTransaction(
                userId = user.id, amount = BigDecimal("100.00"),
                type = TransactionType.DEPOSIT, idempotencyKey = "s1",
                createdAt = hourAgo
            )
        )
        balanceTransactionRepository.save(
            BalanceTransaction(
                userId = user.id, amount = BigDecimal("-30.00"),
                type = TransactionType.SESSION_DEBIT, idempotencyKey = "s2",
                createdAt = now
            )
        )
        balanceTransactionRepository.save(
            BalanceTransaction(
                userId = user.id, amount = BigDecimal("1000.00"),
                type = TransactionType.DEPOSIT, idempotencyKey = "s3",
                createdAt = twoHoursAgo
            )
        )

        val sum = balanceTransactionRepository.sumAmountByUserIdAndPeriod(
            user.id, hourAgo.minusSeconds(60), dayAhead
        )

        assertThat(sum).isEqualByComparingTo(BigDecimal("70.00"))
    }

    @Test
    fun `should count transactions by user`() {
        val user1 = createTestUser(keycloakId = "k1-${UUID.randomUUID()}")
        val user2 = createTestUser(keycloakId = "k2-${UUID.randomUUID()}")

        repeat(3) {
            balanceTransactionRepository.save(
                BalanceTransaction(
                    userId = user1.id, amount = BigDecimal("10.00"),
                    type = TransactionType.BONUS, idempotencyKey = "u1-$it"
                )
            )
        }
        balanceTransactionRepository.save(
            BalanceTransaction(
                userId = user2.id, amount = BigDecimal("20.00"),
                type = TransactionType.BONUS, idempotencyKey = "u2-0"
            )
        )

        assertThat(balanceTransactionRepository.countByUserId(user1.id)).isEqualTo(3)
        assertThat(balanceTransactionRepository.countByUserId(user2.id)).isEqualTo(1)
    }

    @Test
    fun `should filter transactions by type`() {
        val user = createTestUser(keycloakId = "k1-${UUID.randomUUID()}")

        balanceTransactionRepository.save(
            BalanceTransaction(
                userId = user.id, amount = BigDecimal("500.00"),
                type = TransactionType.DEPOSIT, idempotencyKey = "d1"
            )
        )
        balanceTransactionRepository.save(
            BalanceTransaction(
                userId = user.id, amount = BigDecimal("-100.00"),
                type = TransactionType.SESSION_DEBIT, idempotencyKey = "d2"
            )
        )
        balanceTransactionRepository.save(
            BalanceTransaction(
                userId = user.id, amount = BigDecimal("50.00"),
                type = TransactionType.REFUND, idempotencyKey = "d3"
            )
        )

        val deposits = balanceTransactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(
            user.id, TransactionType.DEPOSIT, PageRequest.of(0, 10)
        )

        assertThat(deposits.content).hasSize(1)
        assertThat(deposits.content[0].type).isEqualTo(TransactionType.DEPOSIT)
    }
}