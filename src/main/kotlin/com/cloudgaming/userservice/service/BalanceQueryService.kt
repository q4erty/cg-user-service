package com.cloudgaming.userservice.service

import com.cloudgaming.userservice.common.exception.TransactionNotFoundException
import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.domain.BalanceTransaction
import com.cloudgaming.userservice.dto.BalanceDto
import com.cloudgaming.userservice.dto.TransactionDto
import com.cloudgaming.userservice.persistence.BalanceTransactionRepository
import com.cloudgaming.userservice.persistence.UserBalanceRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BalanceQueryService(
    private val userBalanceRepository: UserBalanceRepository,
    private val balanceTransactionRepository: BalanceTransactionRepository
) {

    companion object {
        const val CACHE_NAME = "user_balance"
        private const val SORT_PROPERTY = "createdAt"
        private const val MAX_PAGE_SIZE = 100
    }

    @Cacheable(value = [CACHE_NAME], key = "#userId")
    @Transactional(readOnly = true)
    fun getBalance(userId: UUID): BalanceDto {
        val balance = userBalanceRepository.findByUserId(userId)
            ?: throw UserNotFoundException.byUserId(userId)

        return BalanceDto(
            amount = balance.amount,
            currency = balance.currency,
            lastOperationAt = balance.lastOperationAt
        )
    }

    @Transactional(readOnly = true)
    fun getTransactionHistory(userId: UUID, page: Int, size: Int, sort: String): Page<TransactionDto> {
        val pageable = buildPageable(page, size, sort)
        return balanceTransactionRepository.findByUserId(userId, pageable).map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun getTransactionById(userId: UUID, transactionId: UUID): TransactionDto {
        val transaction = balanceTransactionRepository.findByIdAndUserId(transactionId, userId)
            ?: throw TransactionNotFoundException(transactionId, userId)
        return transaction.toDto()
    }

    private fun buildPageable(page: Int, size: Int, sort: String): Pageable {
        require(page >= 0) { "page must not be negative" }
        require(size in 1..MAX_PAGE_SIZE) { "size must be between 1 and $MAX_PAGE_SIZE" }

        val parts = sort.split(",")
        val property = parts.getOrNull(0)?.trim().orEmpty()
        if (property != SORT_PROPERTY) {
            throw IllegalArgumentException(
                "Unsupported sort property '$property', only '$SORT_PROPERTY' is allowed"
            )
        }
        val direction = parts.getOrNull(1)?.trim()
            ?.let { Sort.Direction.fromString(it) }
            ?: Sort.Direction.DESC

        return PageRequest.of(page, size, Sort.by(direction, property))
    }

    private fun BalanceTransaction.toDto() = TransactionDto(
        id = id,
        amount = amount,
        type = type.name,
        sessionId = sessionId,
        paymentId = paymentId,
        createdAt = createdAt,
        description = description
    )
}
