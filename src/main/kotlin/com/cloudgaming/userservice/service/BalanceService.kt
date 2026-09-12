package com.cloudgaming.userservice.service

import com.cloudgaming.userservice.dto.BalanceOperationRequest
import com.cloudgaming.userservice.dto.BalanceOperationResponse
import com.cloudgaming.userservice.dto.CachedIdempotentResult
import com.cloudgaming.userservice.common.exception.ConcurrentBalanceOperationException
import com.cloudgaming.userservice.common.exception.IdempotencyConflictException
import com.cloudgaming.userservice.common.exception.InsufficientFundsException
import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.constants.BalanceLock
import com.cloudgaming.userservice.constants.RedisKey
import com.cloudgaming.userservice.domain.BalanceTransaction
import com.cloudgaming.userservice.domain.TransactionType
import com.cloudgaming.userservice.events.UserEventProducer
import com.cloudgaming.userservice.persistence.BalanceTransactionRepository
import com.cloudgaming.userservice.persistence.UserBalanceRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.resilience4j.retry.annotation.Retry
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class BalanceService(
    private val userBalanceRepository: UserBalanceRepository,
    private val balanceTransactionRepository: BalanceTransactionRepository,
    private val redissonClient: RedissonClient,
    private val stringRedisTemplate: StringRedisTemplate,
    private val eventProducer: UserEventProducer,
    private val objectMapper: ObjectMapper,
    @Value("\${app.balance.low-threshold:100.00}") private val lowThreshold: BigDecimal
) {

    private val logger = LoggerFactory.getLogger(BalanceService::class.java)

    @Retry(name = "balance-optimistic-lock", fallbackMethod = "recoverFromOptimisticLockFailure")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun applyOperation(userId: UUID, request: BalanceOperationRequest): BalanceOperationResponse {
        val redisKey = RedisKey.BALANCE_IDEMPOTENCY_PREFIX + sha256("$userId:${request.idempotencyKey}")

        tryGetCachedResult(redisKey, userId, request)?.let { return it }

        stringRedisTemplate.opsForValue().setIfAbsent(
            redisKey,
            RedisKey.BALANCE_PENDING_MARKER,
            RedisKey.BALANCE_IDEMPOTENCY_TTL
        )

        val lock = redissonClient.getLock(RedisKey.BALANCE_LOCK_PREFIX + userId)
        val acquired = try {
            lock.tryLock(BalanceLock.WAIT_SECONDS, BalanceLock.LEASE_SECONDS, TimeUnit.SECONDS)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ConcurrentBalanceOperationException(userId, "Interrupted while acquiring lock for user $userId")
        }

        if (!acquired) {
            throw ConcurrentBalanceOperationException(userId)
        }

        val releaseLockAfterCompletion = TransactionSynchronizationManager.isSynchronizationActive()
        if (releaseLockAfterCompletion) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (lock.isHeldByCurrentThread) {
                        lock.unlock()
                    }
                }
            })
        }

        try {
            tryGetCachedResult(redisKey, userId, request)?.let { return it }

            val type = request.type

            val balance = userBalanceRepository.findByUserId(userId)
                ?: throw UserNotFoundException.byUserId(userId)

            val signedAmount = when (type) {
                TransactionType.SESSION_DEBIT -> {
                    if (balance.amount < request.amount) {
                        throw InsufficientFundsException(userId, balance.amount, request.amount)
                    }
                    balance.amount = balance.amount.subtract(request.amount)
                    request.amount.negate()
                }

                else -> {
                    balance.amount = balance.amount.add(request.amount)
                    request.amount
                }
            }
            balance.lastOperationAt = Instant.now()

            val savedBalance = userBalanceRepository.save(balance)

            val transaction = balanceTransactionRepository.save(
                BalanceTransaction(
                    userId = userId,
                    amount = signedAmount,
                    type = type,
                    sessionId = request.sessionId,
                    paymentId = request.paymentId,
                    idempotencyKey = request.idempotencyKey,
                    description = request.description
                )
            )

            val response = BalanceOperationResponse(
                transactionId = transaction.id,
                newBalance = savedBalance.amount,
                currency = savedBalance.currency,
                processedAt = Instant.now()
            )

            scheduleCacheWrite(redisKey, userId, request, response)

            eventProducer.publishBalanceOperationApplied(
                userId = userId,
                transactionId = transaction.id,
                type = type.name,
                amount = signedAmount,
                newBalance = savedBalance.amount
            )
            if (savedBalance.amount < lowThreshold) {
                eventProducer.publishBalanceLow(userId, savedBalance.amount, lowThreshold)
            }

            return response
        } catch (ex: IdempotencyConflictException) {
            throw ex
        } catch (ex: Exception) {
            stringRedisTemplate.delete(redisKey)
            throw ex
        } finally {
            if (!releaseLockAfterCompletion && lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    @Suppress("unused")
    private fun recoverFromOptimisticLockFailure(
        userId: UUID,
        request: BalanceOperationRequest,
        ex: ObjectOptimisticLockingFailureException
    ): BalanceOperationResponse {
        logger.warn("Exhausted optimistic-lock retries while applying balance operation for user {}", userId, ex)
        throw ConcurrentBalanceOperationException(
            userId,
            "Too many concurrent balance updates for user $userId, please retry"
        )
    }

    private fun tryGetCachedResult(
        redisKey: String,
        userId: UUID,
        request: BalanceOperationRequest
    ): BalanceOperationResponse? {
        val raw = stringRedisTemplate.opsForValue().get(redisKey) ?: return null
        if (raw == RedisKey.BALANCE_PENDING_MARKER) {
            return null
        }

        val cached: CachedIdempotentResult = objectMapper.readValue(raw, CachedIdempotentResult::class.java)
        if (cached.matches(userId, request)) {
            return cached.response
        }

        throw IdempotencyConflictException(request.idempotencyKey, cached.response.transactionId)
    }

    private fun scheduleCacheWrite(
        redisKey: String,
        userId: UUID,
        request: BalanceOperationRequest,
        response: BalanceOperationResponse
    ) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    cacheResult(redisKey, userId, request, response)
                }
            })
        } else {
            cacheResult(redisKey, userId, request, response)
        }
    }

    private fun cacheResult(
        redisKey: String,
        userId: UUID,
        request: BalanceOperationRequest,
        response: BalanceOperationResponse
    ) {
        val cached = CachedIdempotentResult(
            fingerprint = CachedIdempotentResult.fingerprintOf(userId, request),
            response = response
        )
        val json = objectMapper.writeValueAsString(cached)
        stringRedisTemplate.opsForValue().set(redisKey, json, RedisKey.BALANCE_IDEMPOTENCY_TTL)
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
