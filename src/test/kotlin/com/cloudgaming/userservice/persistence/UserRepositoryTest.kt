package com.cloudgaming.userservice.persistence

import com.cloudgaming.userservice.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class UserRepositoryTest : AbstractJpaTest() {

    @Test
    fun `should save and find user by keycloak_id`() {
        val keycloakId = "keycloak-${UUID.randomUUID()}"
        val user = createTestUser(
            keycloakId = keycloakId,
            email = "alice@example.com",
            displayName = "Alice"
        )

        val found = userRepository.findByKeycloakId(keycloakId)

        assertThat(found).isNotNull
        assertThat(found!!.id).isEqualTo(user.id)
        assertThat(found.email).isEqualTo("alice@example.com")
        assertThat(found.displayName).isEqualTo("Alice")
        assertThat(found.createdAt).isNotNull
        assertThat(found.updatedAt).isNotNull
    }

    @Test
    fun `should return null when keycloak_id not found`() {
        val found = userRepository.findByKeycloakId("non-existent-id")
        assertThat(found).isNull()
    }

    @Test
    fun `existsByKeycloakId returns true for existing user`() {
        val keycloakId = "exists-${UUID.randomUUID()}"
        createTestUser(keycloakId = keycloakId)

        assertThat(userRepository.existsByKeycloakId(keycloakId)).isTrue
        assertThat(userRepository.existsByKeycloakId("non-existent")).isFalse
    }

    @Test
    fun `should enforce unique constraint on keycloak_id`() {
        val keycloakId = "duplicate-${UUID.randomUUID()}"
        createTestUser(keycloakId = keycloakId, email = "first@example.com")

        val duplicate = User(
            keycloakId = keycloakId,
            email = "second@example.com"
        )

        assertThrows<DataIntegrityViolationException> {
            userRepository.save(duplicate)
            userRepository.flush()
        }
    }

    @Test
    fun `should enforce unique constraint on email (case-insensitive)`() {
        val email = "Test@Example.COM"
        createTestUser(keycloakId = "k1-${UUID.randomUUID()}", email = email)

        val duplicate = User(
            keycloakId = "k2-${UUID.randomUUID()}",
            email = "test@example.com"
        )

        assertThrows<DataIntegrityViolationException> {
            userRepository.save(duplicate)
            userRepository.flush()
        }
    }

    @Test
    fun `should update lastLoginAt via JPQL query`() {
        val keycloakId = "login-${UUID.randomUUID()}"
        createTestUser(keycloakId = keycloakId)

        val newLoginTime = Instant.now()
        val updatedRows = userRepository.updateLastLoginAt(keycloakId, newLoginTime)

        assertThat(updatedRows).isEqualTo(1)

        entityManager.clear()
        val found = userRepository.findByKeycloakId(keycloakId)
        assertThat(found!!.lastLoginAt).isCloseTo(newLoginTime, within(1, ChronoUnit.SECONDS))
    }

    @Test
    fun `should update updatedAt automatically via @PreUpdate`() {
        val keycloakId = "update-${UUID.randomUUID()}"
        val user = createTestUser(keycloakId = keycloakId)

        val originalUpdatedAt = user.updatedAt

        Thread.sleep(50)

        user.email = "newemail@example.com"
        userRepository.save(user)
        userRepository.flush()
        entityManager.refresh(user)

        assertThat(user.updatedAt).isAfter(originalUpdatedAt)
    }

    @Test
    fun `should find users by email containing ignore case`() {
        createTestUser(keycloakId = "k1", email = "alice@example.com")
        createTestUser(keycloakId = "k2", email = "bob@example.com")
        createTestUser(keycloakId = "k3", email = "ALICE@other.org")

        val page = userRepository.findByEmailContainingIgnoreCase("alice", PageRequest.of(0, 10))

        assertThat(page.content).hasSize(2)
        assertThat(page.content).allMatch { it.email.lowercase().contains("alice") }
    }

    @Test
    fun `should delete user and cascade delete balance`() {
        val user = createTestUser(keycloakId = "delete-${UUID.randomUUID()}")
        assertThat(userBalanceRepository.existsByUserId(user.id)).isTrue

        userRepository.flush()
        entityManager.clear()

        userRepository.deleteById(user.id)
        userRepository.flush()

        assertThat(userRepository.findById(user.id)).isEmpty
        assertThat(userBalanceRepository.existsByUserId(user.id)).isFalse
    }
}