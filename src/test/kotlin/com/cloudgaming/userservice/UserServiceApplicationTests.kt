package com.cloudgaming.userservice

import com.cloudgaming.userservice.container.KafkaTestContainerSingleton
import com.cloudgaming.userservice.container.KeycloakTestContainerSingleton
import com.cloudgaming.userservice.container.PostgresTestContainerSingleton
import com.cloudgaming.userservice.container.RedisTestContainerSingleton
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class UserServiceApplicationTests {

    companion object {
        private val postgres = PostgresTestContainerSingleton.instance
        private val redis = RedisTestContainerSingleton.instance
        private val kafka = KafkaTestContainerSingleton.instance
        private val keycloak = KeycloakTestContainerSingleton.instance

        @JvmStatic
        @DynamicPropertySource
        fun overrideProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("spring.data.redis.password") { RedisTestContainerSingleton.PASSWORD }
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
            registry.add("keycloak.admin.server-url") { keycloak.authServerUrl }
            registry.add("keycloak.admin.realm") { KeycloakTestContainerSingleton.MASTER_REALM }
            registry.add("keycloak.admin.target-realm") { KeycloakTestContainerSingleton.TARGET_REALM }
            registry.add("keycloak.admin.client-id") { "admin-cli" }
            registry.add("keycloak.admin.client-secret") { "admin" }
        }
    }

    @Test
    fun contextLoads() {
    }
}