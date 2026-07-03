package com.cloudgaming.userservice.integration.keycloak

import com.cloudgaming.userservice.exception.KeycloakApiException
import com.cloudgaming.userservice.exception.RoleNotFoundException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.WebApplicationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.*
import org.keycloak.representations.idm.RoleRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.*

class KeycloakAdminClientUnitTest {

    private val keycloak: Keycloak = mock()
    private val adminClient = KeycloakAdminClient(keycloak, targetRealm = "cloud-gaming")

    private val realmResource: RealmResource = mock()
    private val usersResource: UsersResource = mock()
    private val rolesResource: RolesResource = mock()
    private val userResource: UserResource = mock()
    private val roleResource: RoleResource = mock()
    private val roleMappingResource = mock<RoleScopeResource>()
    private val roleMappingResourceBuilder = mock<RoleMappingResource>()

    init {
        whenever(keycloak.realm("cloud-gaming")).thenReturn(realmResource)
        whenever(realmResource.users()).thenReturn(usersResource)
        whenever(realmResource.roles()).thenReturn(rolesResource)
    }

    @Nested
    inner class AssignRole {

        @Test
        fun `should assign role when role exists`() {
            val keycloakUserId = UUID.randomUUID().toString()
            val roleName = "PLAYER"
            val roleRep = RoleRepresentation().apply { name = roleName }

            whenever(rolesResource.get(roleName)).thenReturn(roleResource)
            whenever(roleResource.toRepresentation()).thenReturn(roleRep)
            whenever(usersResource.get(keycloakUserId)).thenReturn(userResource)
            whenever(userResource.roles()).thenReturn(roleMappingResourceBuilder)
            whenever(roleMappingResourceBuilder.realmLevel()).thenReturn(roleMappingResource)

            adminClient.assignRole(keycloakUserId, roleName)

            verify(roleMappingResource).add(listOf(roleRep))
        }

        @Test
        fun `should throw RoleNotFoundException when role does not exist`() {
            val keycloakUserId = UUID.randomUUID().toString()
            whenever(rolesResource.get("UNKNOWN")).thenReturn(roleResource)
            whenever(roleResource.toRepresentation()).thenThrow(NotFoundException("Not found"))

            assertThatThrownBy {
                adminClient.assignRole(keycloakUserId, "UNKNOWN")
            }.isInstanceOf(RoleNotFoundException::class.java)
        }

        @Test
        fun `should throw KeycloakApiException on unexpected error`() {
            val keycloakUserId = UUID.randomUUID().toString()
            whenever(rolesResource.get("PLAYER")).thenReturn(roleResource)
            whenever(roleResource.toRepresentation())
                .thenThrow(WebApplicationException("Server error"))

            assertThatThrownBy {
                adminClient.assignRole(keycloakUserId, "PLAYER")
            }.isInstanceOf(KeycloakApiException::class.java)
        }
    }

    @Nested
    inner class RemoveRole {

        @Test
        fun `should remove role when exists`() {
            val keycloakUserId = UUID.randomUUID().toString()
            val roleRep = RoleRepresentation().apply { name = "PREMIUM" }

            whenever(rolesResource.get("PREMIUM")).thenReturn(roleResource)
            whenever(roleResource.toRepresentation()).thenReturn(roleRep)
            whenever(usersResource.get(keycloakUserId)).thenReturn(userResource)
            whenever(userResource.roles()).thenReturn(roleMappingResourceBuilder)
            whenever(roleMappingResourceBuilder.realmLevel()).thenReturn(roleMappingResource)

            adminClient.removeRole(keycloakUserId, "PREMIUM")

            verify(roleMappingResource).remove(listOf(roleRep))
        }
    }

    @Nested
    inner class GetUserRoles {

        @Test
        fun `should return role names set`() {
            val keycloakUserId = UUID.randomUUID().toString()
            val roles = listOf(
                RoleRepresentation().apply { name = "PLAYER" },
                RoleRepresentation().apply { name = "offline_access" }
            )

            whenever(usersResource.get(keycloakUserId)).thenReturn(userResource)
            whenever(userResource.roles()).thenReturn(roleMappingResourceBuilder)
            whenever(roleMappingResourceBuilder.realmLevel()).thenReturn(roleMappingResource)
            whenever(roleMappingResource.listAll()).thenReturn(roles)

            val result = adminClient.getUserRoles(keycloakUserId)

            assertThat(result).containsExactlyInAnyOrder("PLAYER", "offline_access")
        }

        @Test
        fun `should throw KeycloakApiException on error`() {
            val keycloakUserId = UUID.randomUUID().toString()
            whenever(usersResource.get(keycloakUserId)).thenReturn(userResource)
            whenever(userResource.roles()).thenReturn(roleMappingResourceBuilder)
            whenever(roleMappingResourceBuilder.realmLevel()).thenReturn(roleMappingResource)
            whenever(roleMappingResource.listAll()).thenThrow(RuntimeException("Network error"))

            assertThatThrownBy {
                adminClient.getUserRoles(keycloakUserId)
            }.isInstanceOf(KeycloakApiException::class.java)
        }
    }

    @Nested
    inner class FindByEmail {

        @Test
        fun `should return user when found`() {
            val user = UserRepresentation().apply {
                id = UUID.randomUUID().toString()
                email = "alice@example.com"
                username = "alice"
            }
            whenever(usersResource.searchByEmail("alice@example.com", true))
                .thenReturn(listOf(user))

            val result = adminClient.findByEmail("alice@example.com")

            assertThat(result).isNotNull
            assertThat(result!!.email).isEqualTo("alice@example.com")
        }

        @Test
        fun `should return null when not found`() {
            whenever(usersResource.searchByEmail(any(), any())).thenReturn(emptyList())

            val result = adminClient.findByEmail("nonexistent@example.com")

            assertThat(result).isNull()
        }
    }

    @Nested
    inner class FindById {

        @Test
        fun `should return user when found`() {
            val keycloakUserId = UUID.randomUUID().toString()
            val user = UserRepresentation().apply {
                id = keycloakUserId
                email = "bob@example.com"
            }

            whenever(usersResource.get(keycloakUserId)).thenReturn(userResource)
            whenever(userResource.toRepresentation()).thenReturn(user)

            val result = adminClient.findById(keycloakUserId)

            assertThat(result).isNotNull
            assertThat(result!!.id).isEqualTo(keycloakUserId)
        }

        @Test
        fun `should return null when NotFoundException`() {
            val keycloakUserId = UUID.randomUUID().toString()
            whenever(usersResource.get(keycloakUserId)).thenReturn(userResource)
            whenever(userResource.toRepresentation()).thenThrow(NotFoundException("Not found"))

            val result = adminClient.findById(keycloakUserId)

            assertThat(result).isNull()
        }
    }

    @Nested
    inner class ListRealmRoles {

        @Test
        fun `should return set of role names`() {
            val roles = listOf(
                RoleRepresentation().apply { name = "PLAYER" },
                RoleRepresentation().apply { name = "ADMIN" },
                RoleRepresentation().apply { name = "PREMIUM" }
            )
            whenever(rolesResource.list()).thenReturn(roles)

            val result = adminClient.listRealmRoles()

            assertThat(result).containsExactlyInAnyOrder("PLAYER", "ADMIN", "PREMIUM")
        }
    }
}