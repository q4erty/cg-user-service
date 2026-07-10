package com.cloudgaming.userservice.domain

import jakarta.persistence.*
import org.springframework.data.domain.Persistable
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@Entity
@Table(name = "user_balances")
class UserBalance(

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    val userId: UUID,

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    var user: User? = null,

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    var amount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "currency", nullable = false, length = 3)
    var currency: String = "KZT",

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0L,

    @Column(name = "last_operation_at")
    var lastOperationAt: Instant? = null

) : Persistable<UUID> {

    @Transient
    private var _isNew = true

    override fun getId(): UUID = userId
    override fun isNew(): Boolean = _isNew

    @PostLoad
    @PostPersist
    fun markNotNew() {
        _isNew = false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserBalance) return false
        return userId == other.userId
    }

    override fun hashCode(): Int = userId.hashCode()

    override fun toString(): String =
        "UserBalance(userId=$userId, amount=$amount, currency='$currency', version=$version)"
}