package com.cloudgaming.userservice.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@Entity
@Table(name = "balance_transactions")
class BalanceTransaction(

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    val type: TransactionType,

    @Column(name = "session_id")
    val sessionId: UUID? = null,

    @Column(name = "payment_id")
    val paymentId: UUID? = null,

    @Column(name = "idempotency_key", nullable = false, length = 255)
    val idempotencyKey: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "description", length = 500)
    val description: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BalanceTransaction) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "BalanceTransaction(id=$id, userId=$userId, amount=$amount, type=$type, idempotencyKey='$idempotencyKey')"
}
