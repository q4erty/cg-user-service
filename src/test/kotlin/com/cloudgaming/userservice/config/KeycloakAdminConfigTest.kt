package com.cloudgaming.userservice.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.keycloak.admin.client.Keycloak
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.getBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(classes = [KeycloakAdminConfig::class])
@ActiveProfiles("test")
class KeycloakAdminConfigTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var keycloak: Keycloak

    @Test
    fun `keycloak bean should be created`() {
        assertThat(keycloak).isNotNull
    }

    @Test
    fun `keycloak bean should be singleton by default`() {
        val firstInstance = context.getBean<Keycloak>()
        val secondInstance = context.getBean<Keycloak>()

        assertThat(firstInstance).isSameAs(secondInstance)
    }
}