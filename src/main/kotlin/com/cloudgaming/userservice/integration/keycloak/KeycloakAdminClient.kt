package com.cloudgaming.userservice.integration.keycloak

import com.cloudgaming.userservice.constants.KeycloakDefaults
import com.cloudgaming.userservice.common.exception.KeycloakApiException
import com.cloudgaming.userservice.common.exception.RoleNotFoundException
import com.cloudgaming.userservice.common.exception.UserNotFoundException
import jakarta.ws.rs.NotFoundException
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.RoleRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class KeycloakAdminClient(
    private val keycloak: Keycloak,
    @Value("\${keycloak.admin.target-realm:${KeycloakDefaults.DEFAULT_REALM}}") private val targetRealm: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun assignRole(keycloakUserId: String, roleName: String) {
        try {
            val role = findRealmRole(roleName)
                ?: throw RoleNotFoundException("Role '$roleName' not found in realm '$targetRealm'")

            keycloak.realm(targetRealm)
                .users()[keycloakUserId]
                .roles()
                .realmLevel()
                .add(listOf(role))

            logger.info("Assigned role '{}' to user '{}'", roleName, keycloakUserId)
        } catch (e: RoleNotFoundException) {
            throw e
        } catch (e: NotFoundException) {
            throw UserNotFoundException.byKeycloakId(keycloakUserId, e)
        } catch (e: Exception) {
            logger.error("Failed to assign role '{}' to user '{}': {}", roleName, keycloakUserId, e.message)
            throw KeycloakApiException("Failed to assign role: ${e.message}", e)
        }
    }

    fun removeRole(keycloakUserId: String, roleName: String) {
        try {
            val role = findRealmRole(roleName)
                ?: throw RoleNotFoundException("Role '$roleName' not found in realm '$targetRealm'")

            keycloak.realm(targetRealm)
                .users()[keycloakUserId]
                .roles()
                .realmLevel()
                .remove(listOf(role))

            logger.info("Removed role '{}' from user '{}'", roleName, keycloakUserId)
        } catch (e: RoleNotFoundException) {
            throw e
        } catch (e: NotFoundException) {
            throw UserNotFoundException.byKeycloakId(keycloakUserId, e)
        } catch (e: Exception) {
            logger.error("Failed to remove role '{}' from user '{}': {}", roleName, keycloakUserId, e.message)
            throw KeycloakApiException("Failed to remove role: ${e.message}", e)
        }
    }

    fun getUserRoles(keycloakUserId: String): Set<String> {
        return try {
            keycloak.realm(targetRealm)
                .users()[keycloakUserId]
                .roles()
                .realmLevel()
                .listAll()
                .map { it.name }
                .toSet()
        } catch (e: NotFoundException) {
            throw UserNotFoundException.byKeycloakId(keycloakUserId, e)
        } catch (e: Exception) {
            logger.error("Failed to get roles for user '{}': {}", keycloakUserId, e.message)
            throw KeycloakApiException("Failed to get user roles: ${e.message}", e)
        }
    }

    fun findByEmail(email: String): UserRepresentation? {
        return try {
            keycloak.realm(targetRealm)
                .users()
                .searchByEmail(email, true)
                .firstOrNull()
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            logger.error("Failed to find user by email '{}': {}", email, e.message)
            throw KeycloakApiException("Failed to find user: ${e.message}", e)
        }
    }

    fun findById(keycloakUserId: String): UserRepresentation? {
        return try {
            keycloak.realm(targetRealm)
                .users()[keycloakUserId]
                .toRepresentation()
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            logger.error("Failed to find user by id '{}': {}", keycloakUserId, e.message)
            throw KeycloakApiException("Failed to find user: ${e.message}", e)
        }
    }

    fun listRealmRoles(): Set<String> {
        return try {
            keycloak.realm(targetRealm)
                .roles()
                .list()
                .map { it.name }
                .toSet()
        } catch (e: Exception) {
            logger.error("Failed to list realm roles: {}", e.message)
            throw KeycloakApiException("Failed to list roles: ${e.message}", e)
        }
    }

    private fun findRealmRole(roleName: String): RoleRepresentation? {
        return try {
            keycloak.realm(targetRealm)
                .roles()
                .get(roleName)
                .toRepresentation()
        } catch (e: NotFoundException) {
            null
        }
    }
}
