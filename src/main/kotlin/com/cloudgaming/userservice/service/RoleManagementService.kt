package com.cloudgaming.userservice.service

import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.common.security.SecurityUtils
import com.cloudgaming.userservice.constants.AdminConstants
import com.cloudgaming.userservice.constants.Role
import com.cloudgaming.userservice.domain.User
import com.cloudgaming.userservice.dto.UserRoleChangedEvent
import com.cloudgaming.userservice.events.UserEventProducer
import com.cloudgaming.userservice.integration.keycloak.KeycloakRoleProxy
import com.cloudgaming.userservice.persistence.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional(readOnly = true)
class RoleManagementService(
    private val userRepository: UserRepository,
    private val keycloakRoleProxy: KeycloakRoleProxy,
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
    fun assignRole(targetUserId: UUID, role: Role, adminId: UUID): UserRoleChangedEvent {
        val user = userRepository.findById(targetUserId).orElse(null)
            ?: throw UserNotFoundException.byUserId(targetUserId)

        if (!role.isKeycloakManaged) {
            throw IllegalArgumentException("Role ${role.roleName} cannot be assigned via Keycloak - it is a local role")
        }

        keycloakRoleProxy.assignRole(user.keycloakId, role.roleName)

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
    fun removeRole(targetUserId: UUID, role: Role, adminId: UUID): UserRoleChangedEvent {
        val user = userRepository.findById(targetUserId).orElse(null)
            ?: throw UserNotFoundException.byUserId(targetUserId)

        if (!role.isKeycloakManaged) {
            throw IllegalArgumentException("Role ${role.roleName} cannot be removed via Keycloak - it is a local role")
        }

        keycloakRoleProxy.removeRole(user.keycloakId, role.roleName)

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
}