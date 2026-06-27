package com.cloudgaming.userservice.persistence

import com.cloudgaming.userservice.domain.User
import com.cloudgaming.userservice.domain.UserBalance
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.*

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Transactional
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
abstract class AbstractJpaTest {

    companion object {
        private val postgres = PostgresTestContainerSingleton.instance

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    @Autowired
    protected lateinit var userRepository: UserRepository

    @Autowired
    protected lateinit var userBalanceRepository: UserBalanceRepository

    @Autowired
    protected lateinit var balanceTransactionRepository: BalanceTransactionRepository

    @Autowired
    protected lateinit var entityManager: EntityManager

    protected fun createTestUser(
        keycloakId: String = "test-keycloak-${UUID.randomUUID()}",
        email: String = "test-${UUID.randomUUID()}@example.com",
        displayName: String? = "Test User",
        initialBalance: BigDecimal = BigDecimal.ZERO
    ): User {
        val user = User(
            keycloakId = keycloakId,
            email = email,
            displayName = displayName
        )
        val savedUser = userRepository.save(user)

        val balance = UserBalance(
            userId = savedUser.id,
            user = savedUser,
            amount = initialBalance
        )
        userBalanceRepository.save(balance)

        return savedUser
    }
}