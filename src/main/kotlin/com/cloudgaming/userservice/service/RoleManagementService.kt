package com.cloudgaming.userservice.service

import com.cloudgaming.userservice.common.exception.KeycloakApiException
import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.common.security.SecurityUtils
import com.cloudgaming.userservice.constants.AdminConstants
import com.cloudgaming.userservice.constants.Role
import com.cloudgaming.userservice.domain.User
import com.cloudgaming.userservice.dto.UserRoleChangedEvent
import com.cloudgaming.userservice.events.UserEventProducer
import com.cloudgaming.userservice.integration.keycloak.KeycloakAdminClient
import com.cloudgaming.userservice.persistence.UserRepository
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class RoleManagementService(
    private val userRepository: UserRepository,
    private val keycloakAdminClient: KeycloakAdminClient,
    private val userEventProducer: UserEventProducer,
    private val securityUtils: SecurityUtils
) {

    private val logger = LoggerFactory.getLogger(RoleManagementService::class.java)

    fun findById(internalUserId: UUID): User? {
        return userRepository.findById(internalUserId).orElse(null)
    }

    fun findByKeycloakId(keycloakId: String): User? {
        return userRepository.findByKeycloakId(keycloakId)
    }

    @Transactional
    @CircuitBreaker(name = "keycloak-admin", fallbackMethod = "assignRoleFallback")
    fun assignRole(targetUserId: UUID, role: Role, adminId: UUID): UserRoleChangedEvent {
        val user = userRepository.findById(targetUserId).orElse(null)
            ?: throw UserNotFoundException.byUserId(targetUserId)

        if (!role.isKeycloakManaged) {
            throw IllegalArgumentException("Role ${role.roleName} cannot be assigned via Keycloak - it is a local role")
        }

        keycloakAdminClient.assignRole(user.keycloakId, role.roleName)

        userEventProducer.publishUserRoleChanged(
            targetUserId = targetUserId,
            performedByAdminId = adminId,
            role = role.roleName,
            action = AdminConstants.ACTION_ADD
        )

        return UserRoleChangedEvent(
            targetUserId = targetUserId,
            performedByAdminId = adminId,
            role = role.roleName,
            action = AdminConstants.ACTION_ADD
        )
    }

    @Transactional
    fun assignRoleFallback(targetUserId: UUID, role: Role, adminId: UUID, ex: Throwable): Nothing {
        logger.warn(AdminConstants.ERROR_KEYCLOAK_UNAVAILABLE, ex)
        throw KeycloakApiException(
            AdminConstants.ERROR_KEYCLOAK_UNAVAILABLE,
            ex
        )
    }

    @Transactional
    @CircuitBreaker(name = "keycloak-admin", fallbackMethod = "removeRoleFallback")
    fun removeRole(targetUserId: UUID, role: Role, adminId: UUID): UserRoleChangedEvent {
        val user = userRepository.findById(targetUserId).orElse(null)
            ?: throw UserNotFoundException.byUserId(targetUserId)

        if (!role.isKeycloakManaged) {
            throw IllegalArgumentException("Role ${role.roleName} cannot be removed via Keycloak - it is a local role")
        }

        keycloakAdminClient.removeRole(user.keycloakId, role.roleName)

        userEventProducer.publishUserRoleChanged(
            targetUserId = targetUserId,
            performedByAdminId = adminId,
            role = role.roleName,
            action = AdminConstants.ACTION_REMOVE
        )

        return UserRoleChangedEvent(
            targetUserId = targetUserId,
            performedByAdminId = adminId,
            role = role.roleName,
            action = AdminConstants.ACTION_REMOVE
        )
    }

    @Transactional
    fun removeRoleFallback(targetUserId: UUID, role: Role, adminId: UUID, ex: Throwable): Nothing {
        logger.warn(AdminConstants.ERROR_KEYCLOAK_UNAVAILABLE, ex)
        throw KeycloakApiException(
            AdminConstants.ERROR_KEYCLOAK_UNAVAILABLE,
            ex
        )
    }
}