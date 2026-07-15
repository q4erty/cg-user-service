package com.cloudgaming.userservice.service

import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.dto.UpdateProfileRequest
import com.cloudgaming.userservice.dto.UserProfileDto
import com.cloudgaming.userservice.persistence.UserBalanceRepository
import com.cloudgaming.userservice.persistence.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class ProfileService(
    private val userRepository: UserRepository,
    private val userBalanceRepository: UserBalanceRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val CACHE_NAME = "user_profile"
    }

    @Cacheable(value = [CACHE_NAME], key = "#userId")
    @Transactional(readOnly = true)
    fun getProfile(userId: UUID): UserProfileDto {
        logger.debug("Cache miss: loading profile for user {}", userId)

        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException.byUserId(userId) }

        val balance = userBalanceRepository.findByUserId(userId)
            ?: throw UserNotFoundException.byUserId(userId)

        return UserProfileDto(
            id = user.id,
            email = user.email,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
            balance = balance.amount,
            currency = balance.currency,
            createdAt = user.createdAt,
            lastLoginAt = user.lastLoginAt
        )
    }

    @CacheEvict(value = [CACHE_NAME], key = "#userId")
    fun updateProfile(userId: UUID, request: UpdateProfileRequest): UserProfileDto {
        logger.info("Updating profile for user {}", userId)

        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException.byUserId(userId) }

        user.displayName = request.displayName
        user.avatarUrl = request.avatarUrl
        userRepository.save(user)

        return getProfileUncached(userId)
    }

    @Transactional(readOnly = true)
    fun getProfileUncached(userId: UUID): UserProfileDto {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException.byUserId(userId) }
        val balance = userBalanceRepository.findByUserId(userId)
            ?: throw UserNotFoundException.byUserId(userId)

        return UserProfileDto(
            id = user.id,
            email = user.email,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
            balance = balance.amount,
            currency = balance.currency,
            createdAt = user.createdAt,
            lastLoginAt = user.lastLoginAt
        )
    }
}
