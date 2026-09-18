package com.cloudgaming.userservice.integration.keycloak

import com.cloudgaming.userservice.common.exception.KeycloakApiException
import com.cloudgaming.userservice.constants.AdminConstants
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.stereotype.Service

@Service
class KeycloakRoleProxy(
    private val keycloakAdminClient: KeycloakAdminClient
) {
    @CircuitBreaker(name = "keycloak-admin", fallbackMethod = "assignRoleFallback")
    fun assignRole(keycloakId: String, roleName: String) {
        keycloakAdminClient.assignRole(keycloakId, roleName)
    }

    fun assignRoleFallback(keycloakId: String, roleName: String, ex: Throwable): Nothing {
        throw KeycloakApiException(AdminConstants.ERROR_KEYCLOAK_UNAVAILABLE, ex)
    }

    @CircuitBreaker(name = "keycloak-admin", fallbackMethod = "removeRoleFallback")
    fun removeRole(keycloakId: String, roleName: String) {
        keycloakAdminClient.removeRole(keycloakId, roleName)
    }

    fun removeRoleFallback(keycloakId: String, roleName: String, ex: Throwable): Nothing {
        throw KeycloakApiException(AdminConstants.ERROR_KEYCLOAK_UNAVAILABLE, ex)
    }
}