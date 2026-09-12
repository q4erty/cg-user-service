package com.cloudgaming.userservice.persistence

import com.cloudgaming.userservice.domain.BalanceTransaction
import com.cloudgaming.userservice.domain.TransactionType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@Repository
interface BalanceTransactionRepository : JpaRepository<BalanceTransaction, UUID> {

    fun findByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): Page<BalanceTransaction>

    fun findByUserId(userId: UUID, pageable: Pageable): Page<BalanceTransaction>

    fun findByIdAndUserId(id: UUID, userId: UUID): BalanceTransaction?

    fun findByIdempotencyKey(idempotencyKey: String): BalanceTransaction?

    fun existsByIdempotencyKey(idempotencyKey: String): Boolean

    fun findBySessionId(sessionId: UUID): List<BalanceTransaction>

    fun findByPaymentIdAndType(paymentId: UUID, type: TransactionType): List<BalanceTransaction>

    @Query(
        """
        SELECT COALESCE(SUM(t.amount), 0)
        FROM BalanceTransaction t
        WHERE t.userId = :userId
          AND t.createdAt BETWEEN :from AND :to
    """
    )
    fun sumAmountByUserIdAndPeriod(
        @Param("userId") userId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant
    ): BigDecimal

    fun countByUserId(userId: UUID): Long

    fun findByUserIdAndTypeOrderByCreatedAtDesc(
        userId: UUID,
        type: TransactionType,
        pageable: Pageable
    ): Page<BalanceTransaction>
}