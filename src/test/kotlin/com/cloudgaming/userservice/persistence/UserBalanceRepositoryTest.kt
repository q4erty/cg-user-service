package com.cloudgaming.userservice.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.math.BigDecimal
import java.util.*

class UserBalanceRepositoryTest : AbstractJpaTest() {

    @Test
    fun `should create balance with zero default amount`() {
        val user = createTestUser(keycloakId = "k1-${UUID.randomUUID()}")

        val balance = userBalanceRepository.findByUserId(user.id)

        assertThat(balance).isNotNull
        assertThat(balance!!.amount).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(balance.currency).isEqualTo("KZT")
        assertThat(balance.version).isEqualTo(0L)
    }

    @Test
    fun `should increment version on update`() {
        val user = createTestUser(keycloakId = "k1-${UUID.randomUUID()}", initialBalance = BigDecimal("100.00"))
        val balance = userBalanceRepository.findByUserId(user.id)!!

        val originalVersion = balance.version

        balance.amount = BigDecimal("90.00")
        userBalanceRepository.save(balance)
        userBalanceRepository.flush()
        entityManager.refresh(balance)

        assertThat(balance.version).isEqualTo(originalVersion + 1)
    }

    @Test
    fun `should throw ObjectOptimisticLockingFailureException on stale version`() {
        val user = createTestUser(keycloakId = "k1-${UUID.randomUUID()}", initialBalance = BigDecimal("500.00"))

        val balance1 = userBalanceRepository.findByUserId(user.id)!!
        entityManager.detach(balance1)

        val balance2 = userBalanceRepository.findByUserId(user.id)!!
        balance2.amount = BigDecimal("450.00")
        userBalanceRepository.save(balance2)
        userBalanceRepository.flush()

        balance1.amount = BigDecimal("400.00")

        assertThrows<ObjectOptimisticLockingFailureException> {
            userBalanceRepository.saveAndFlush(balance1)
        }
    }

    @Test
    fun `should reject negative amount via DB check constraint`() {
        val user = createTestUser(keycloakId = "k1-${UUID.randomUUID()}")
        val balance = userBalanceRepository.findByUserId(user.id)!!

        balance.amount = BigDecimal("-50.00")

        assertThrows<DataIntegrityViolationException> {
            userBalanceRepository.saveAndFlush(balance)
        }
    }

    @Test
    fun `should calculate total balance across all users`() {
        entityManager.createQuery("DELETE FROM BalanceTransaction").executeUpdate()
        entityManager.createQuery("DELETE FROM UserBalance").executeUpdate()
        entityManager.createQuery("DELETE FROM User").executeUpdate()
        entityManager.flush()
        entityManager.clear()

        createTestUser(keycloakId = "k1", initialBalance = BigDecimal("100.00"))
        createTestUser(keycloakId = "k2", initialBalance = BigDecimal("200.00"))
        createTestUser(keycloakId = "k3", initialBalance = BigDecimal("300.00"))

        val total = userBalanceRepository.getTotalBalance()

        assertThat(total).isEqualByComparingTo(BigDecimal("600.00"))
    }

    @Test
    fun `should count users with balance below threshold`() {
        entityManager.createQuery("DELETE FROM BalanceTransaction").executeUpdate()
        entityManager.createQuery("DELETE FROM UserBalance").executeUpdate()
        entityManager.createQuery("DELETE FROM User").executeUpdate()
        entityManager.flush()
        entityManager.clear()

        createTestUser(keycloakId = "k1", initialBalance = BigDecimal("50.00"))
        createTestUser(keycloakId = "k2", initialBalance = BigDecimal("150.00"))
        createTestUser(keycloakId = "k3", initialBalance = BigDecimal("99.99"))
        createTestUser(keycloakId = "k4", initialBalance = BigDecimal("1000.00"))

        val count = userBalanceRepository.countByAmountLessThan(BigDecimal("100.00"))

        assertThat(count).isEqualTo(2)
    }

    @Test
    fun `should find balance for update with pessimistic lock`() {
        val user = createTestUser(keycloakId = "k1-${UUID.randomUUID()}", initialBalance = BigDecimal("100.00"))

        val balance = userBalanceRepository.findByIdForUpdate(user.id)

        assertThat(balance).isPresent
        assertThat(balance.get().amount).isEqualByComparingTo(BigDecimal("100.00"))
    }

    @Test
    fun `existsByUserId should return correct result`() {
        val user = createTestUser(keycloakId = "k1-${UUID.randomUUID()}")

        assertThat(userBalanceRepository.existsByUserId(user.id)).isTrue
        assertThat(userBalanceRepository.existsByUserId(UUID.randomUUID())).isFalse
    }
}