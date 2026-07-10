package com.cloudgaming.userservice.service

import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.constants.RedisKey
import com.cloudgaming.userservice.domain.User
import com.cloudgaming.userservice.domain.UserBalance
import com.cloudgaming.userservice.dto.UserEventProducer
import com.cloudgaming.userservice.persistence.UserBalanceRepository
import com.cloudgaming.userservice.persistence.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class UserProvisioningService(
    private val userRepository: UserRepository,
    private val userBalanceRepository: UserBalanceRepository,
    private val redisTemplate: StringRedisTemplate,
    private val eventProducer: UserEventProducer
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun ensureUserExists(keycloakId: String, email: String?, displayName: String?): User {
        val existsKey = "${RedisKey.EXISTS_PREFIX}$keycloakId"
        if (redisTemplate.hasKey(existsKey)) {
            logger.debug("Fast path: user {} exists in Redis cache", keycloakId)
            updateLastLoginAsync(keycloakId)
            return getInternalUser(keycloakId, email, displayName)
        }

        val existing = userRepository.findByKeycloakId(keycloakId)
        if (existing != null) {
            logger.debug("Slow path: user {} found in DB", keycloakId)
            syncEmailIfNeeded(existing, email)
            cacheUserIdMapping(keycloakId, existing.id)
            redisTemplate.opsForValue().set(existsKey, "1", RedisKey.EXISTS_TTL)
            updateLastLoginAsync(keycloakId)
            return existing
        }

        logger.info("Provisioning new user: keycloakId={}", keycloakId)
        val newUser = User(
            keycloakId = keycloakId,
            email = email ?: "$keycloakId@keycloak.local",
            displayName = displayName,
            lastLoginAt = Instant.now()
        )

        return try {
            val savedUser = userRepository.save(newUser)
            userBalanceRepository.save(
                UserBalance(userId = savedUser.id, user = savedUser)
            )
            userRepository.flush()
            userBalanceRepository.flush()
            redisTemplate.opsForValue().set(existsKey, "1", RedisKey.EXISTS_TTL)
            cacheUserIdMapping(keycloakId, savedUser.id)
            eventProducer.publishUserRegistered(savedUser.id, savedUser.email, keycloakId)
            logger.info("Provisioned new user: id={}, keycloakId={}", savedUser.id, keycloakId)
            savedUser
        } catch (e: DataIntegrityViolationException) {
            logger.debug("User {} already created by parallel request", keycloakId)
            return findExistingUser(keycloakId) ?: throw IllegalStateException(
                "DataIntegrityViolationException but user not found after retry", e
            )
        }
    }

    fun getInternalUserId(keycloakId: String): UUID? {
        val cached = redisTemplate.opsForValue().get("${RedisKey.ID_MAPPING_PREFIX}$keycloakId")
        if (cached != null) {
            return UUID.fromString(cached)
        }

        val user = userRepository.findByKeycloakId(keycloakId) ?: return null
        cacheUserIdMapping(keycloakId, user.id)
        return user.id
    }

    fun getByKeycloakId(keycloakId: String): User {
        return userRepository.findByKeycloakId(keycloakId)
            ?: throw UserNotFoundException.byKeycloakId(keycloakId)
    }

    fun requireInternalUserId(keycloakId: String): UUID {
        return getInternalUserId(keycloakId)
            ?: throw UserNotFoundException.byKeycloakId(keycloakId)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private fun findExistingUser(keycloakId: String): User? {
        return userRepository.findByKeycloakId(keycloakId)
    }

    private fun getInternalUser(keycloakId: String, email: String?, displayName: String?): User {
        val user = userRepository.findByKeycloakId(keycloakId)
            ?: throw UserNotFoundException.byKeycloakId(keycloakId)
        syncEmailIfNeeded(user, email)
        cacheUserIdMapping(keycloakId, user.id)
        return user
    }

    private fun syncEmailIfNeeded(user: User, emailFromJwt: String?) {
        if (emailFromJwt != null && user.email != emailFromJwt) {
            logger.info("Syncing email for user {}: '{}' → '{}'", user.keycloakId, user.email, emailFromJwt)
            user.email = emailFromJwt
            userRepository.save(user)
        }
    }

    private fun updateLastLoginAsync(keycloakId: String) {
        try {
            userRepository.updateLastLoginAt(keycloakId, Instant.now())
        } catch (e: Exception) {
            logger.warn("Failed to update lastLoginAt for {}: {}", keycloakId, e.message)
        }
    }

    private fun cacheUserIdMapping(keycloakId: String, userId: UUID) {
        redisTemplate.opsForValue().set(
            "${RedisKey.ID_MAPPING_PREFIX}$keycloakId",
            userId.toString(),
            RedisKey.ID_MAPPING_TTL
        )
    }
}