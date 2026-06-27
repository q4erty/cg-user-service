package com.cloudgaming.userservice.persistence

import com.cloudgaming.userservice.domain.UserBalance
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.*

@Repository
interface UserBalanceRepository : JpaRepository<UserBalance, UUID> {

    fun findByUserId(userId: UUID): UserBalance?

    fun existsByUserId(userId: UUID): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM UserBalance b WHERE b.userId = :userId")
    fun findByIdForUpdate(@Param("userId") userId: UUID): Optional<UserBalance>

    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM UserBalance b")
    fun getTotalBalance(): BigDecimal

    @Query("SELECT COUNT(b) FROM UserBalance b WHERE b.amount < :threshold")
    fun countByAmountLessThan(@Param("threshold") threshold: BigDecimal): Long
}