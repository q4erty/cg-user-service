package com.cloudgaming.userservice.integration.keycloak

import com.cloudgaming.userservice.container.KeycloakTestContainerSingleton
import com.cloudgaming.userservice.exception.RoleNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.keycloak.representations.idm.UserRepresentation

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KeycloakAdminClientIntegrationTest {

    private val keycloak = KeycloakTestContainerSingleton.instance

    private lateinit var adminClient: KeycloakAdminClient

    @BeforeAll
    fun setUp() {
        val keycloakClient = KeycloakBuilder.builder()
            .serverUrl(keycloak.authServerUrl)
            .realm(KeycloakTestContainerSingleton.MASTER_REALM)
            .grantType(OAuth2Constants.PASSWORD)
            .clientId("admin-cli")
            .username(KeycloakTestContainerSingleton.ADMIN_USERNAME)
            .password(KeycloakTestContainerSingleton.ADMIN_PASSWORD)
            .build()

        val realms = keycloakClient.realms().findAll()
        if (realms.none { it.realm == KeycloakTestContainerSingleton.TARGET_REALM }) {
            val realmRep = RealmRepresentation().apply {
                realm = KeycloakTestContainerSingleton.TARGET_REALM
                isEnabled = true
            }
            keycloakClient.realms().create(realmRep)
        }

        val roleManager = keycloakClient.realm(KeycloakTestContainerSingleton.TARGET_REALM).roles()
        val roleNames = listOf("PLAYER", "PREMIUM", "ADMIN")
        for (roleName in roleNames) {
            try {
                roleManager.get(roleName).toRepresentation()
            } catch (e: jakarta.ws.rs.NotFoundException) {
                val roleRep = RoleRepresentation().apply {
                    name = roleName
                    description = "Role $roleName"
                    isComposite = false
                }
                roleManager.create(roleRep)
            }
        }

        adminClient = KeycloakAdminClient(keycloakClient, KeycloakTestContainerSingleton.TARGET_REALM)

        val existing = keycloakClient.realm(KeycloakTestContainerSingleton.TARGET_REALM)
            .users().searchByUsername("test-user@example.com", true)

        if (existing.isEmpty()) {
            val user = UserRepresentation().apply {
                username = "test-user@example.com"
                email = "test-user@example.com"
                isEnabled = true
                isEmailVerified = true
                credentials = listOf(CredentialRepresentation().apply {
                    type = CredentialRepresentation.PASSWORD
                    value = "test123"
                    isTemporary = false
                })
            }
            keycloakClient.realm(KeycloakTestContainerSingleton.TARGET_REALM).users().create(user)
        }
    }

    @Test
    fun `should find user by email`() {
        val user = adminClient.findByEmail("test-user@example.com")

        assertThat(user).isNotNull
        assertThat(user!!.username).isEqualTo("test-user@example.com")
    }

    @Test
    fun `should assign and get user roles`() {
        val user = adminClient.findByEmail("test-user@example.com")!!
        val keycloakUserId = user.id

        adminClient.assignRole(keycloakUserId, "PLAYER")

        val roles = adminClient.getUserRoles(keycloakUserId)
        assertThat(roles).contains("PLAYER")
    }

    @Test
    fun `should remove role from user`() {
        val user = adminClient.findByEmail("test-user@example.com")!!
        val keycloakUserId = user.id

        adminClient.assignRole(keycloakUserId, "PREMIUM")
        assertThat(adminClient.getUserRoles(keycloakUserId)).contains("PREMIUM")

        adminClient.removeRole(keycloakUserId, "PREMIUM")
        assertThat(adminClient.getUserRoles(keycloakUserId)).doesNotContain("PREMIUM")
    }

    @Test
    fun `should throw RoleNotFoundException for unknown role`() {
        val user = adminClient.findByEmail("test-user@example.com")!!
        val keycloakUserId = user.id

        assertThatThrownBy {
            adminClient.assignRole(keycloakUserId, "NON_EXISTENT_ROLE")
        }.isInstanceOf(RoleNotFoundException::class.java)
    }

    @Test
    fun `should list realm roles`() {
        val roles = adminClient.listRealmRoles()

        assertThat(roles).contains("PLAYER", "PREMIUM", "ADMIN")
    }

    @Test
    fun `should return null when user not found by email`() {
        val user = adminClient.findByEmail("nonexistent@example.com")
        assertThat(user).isNull()
    }
}
