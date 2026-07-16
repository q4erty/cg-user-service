package com.cloudgaming.userservice.service

import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.domain.User
import com.cloudgaming.userservice.domain.UserBalance
import com.cloudgaming.userservice.dto.UpdateProfileRequest
import com.cloudgaming.userservice.persistence.UserBalanceRepository
import com.cloudgaming.userservice.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class ProfileServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var userBalanceRepository: UserBalanceRepository

    @InjectMocks
    private lateinit var profileService: ProfileService

    private val testUserId = UUID.randomUUID()

    private fun testUser() = User(
        id = testUserId,
        keycloakId = "kc-test",
        email = "alice@example.com",
        displayName = "Alice",
        avatarUrl = "https://example.com/avatar.png",
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        lastLoginAt = Instant.parse("2026-06-19T12:00:00Z")
    )

    private fun testBalance() = UserBalance(
        userId = testUserId,
        user = testUser(),
        amount = BigDecimal("150.00"),
        currency = "RUB",
        version = 0L,
        lastOperationAt = null
    )

    @Nested
    inner class GetProfile {

        @Test
        fun `should return profile when user exists`() {
            whenever(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser()))
            whenever(userBalanceRepository.findByUserId(testUserId)).thenReturn(testBalance())

            val result = profileService.getProfile(testUserId)

            assertThat(result.id).isEqualTo(testUserId)
            assertThat(result.email).isEqualTo("alice@example.com")
            assertThat(result.displayName).isEqualTo("Alice")
            assertThat(result.avatarUrl).isEqualTo("https://example.com/avatar.png")
            assertThat(result.balance).isEqualByComparingTo(BigDecimal("150.00"))
            assertThat(result.currency).isEqualTo("RUB")
            assertThat(result.createdAt).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"))
            assertThat(result.lastLoginAt).isEqualTo(Instant.parse("2026-06-19T12:00:00Z"))
        }

        @Test
        fun `should throw UserNotFoundException when user not in DB`() {
            whenever(userRepository.findById(testUserId)).thenReturn(Optional.empty())

            assertThatThrownBy { profileService.getProfile(testUserId) }
                .isInstanceOf(UserNotFoundException::class.java)
        }

        @Test
        fun `should throw UserNotFoundException when balance not found`() {
            whenever(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser()))
            whenever(userBalanceRepository.findByUserId(testUserId)).thenReturn(null)

            assertThatThrownBy { profileService.getProfile(testUserId) }
                .isInstanceOf(UserNotFoundException::class.java)
        }
    }

    @Nested
    inner class UpdateProfile {

        @Test
        fun `should update displayName and avatarUrl`() {
            val user = testUser()
            whenever(userRepository.findById(testUserId)).thenReturn(Optional.of(user))
            whenever(userBalanceRepository.findByUserId(testUserId)).thenReturn(testBalance())
            whenever(userRepository.save(any())).thenAnswer { it.getArgument(0) }

            val request = UpdateProfileRequest(
                displayName = "Alice Updated",
                avatarUrl = "https://example.com/new.png"
            )

            val result = profileService.updateProfile(testUserId, request)

            assertThat(result.displayName).isEqualTo("Alice Updated")
            assertThat(result.avatarUrl).isEqualTo("https://example.com/new.png")
            verify(userRepository).save(argWhere { it.displayName == "Alice Updated" })
        }

        @Test
        fun `should allow null avatarUrl`() {
            val user = testUser()
            whenever(userRepository.findById(testUserId)).thenReturn(Optional.of(user))
            whenever(userBalanceRepository.findByUserId(testUserId)).thenReturn(testBalance())
            whenever(userRepository.save(any())).thenAnswer { it.getArgument(0) }

            val request = UpdateProfileRequest(displayName = "Alice", avatarUrl = null)

            val result = profileService.updateProfile(testUserId, request)

            assertThat(result.avatarUrl).isNull()
            verify(userRepository).save(argWhere { it.avatarUrl == null })
        }

        @Test
        fun `should throw UserNotFoundException when user not in DB`() {
            whenever(userRepository.findById(testUserId)).thenReturn(Optional.empty())

            val request = UpdateProfileRequest(displayName = "Alice", avatarUrl = null)

            assertThatThrownBy { profileService.updateProfile(testUserId, request) }
                .isInstanceOf(UserNotFoundException::class.java)

            verify(userRepository, never()).save(any())
        }
    }
}