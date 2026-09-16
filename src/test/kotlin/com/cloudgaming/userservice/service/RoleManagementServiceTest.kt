package com.cloudgaming.userservice.service

import com.cloudgaming.userservice.common.exception.KeycloakApiException
import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.common.security.SecurityUtils
import com.cloudgaming.userservice.constants.Role
import com.cloudgaming.userservice.domain.User
import com.cloudgaming.userservice.dto.UserRoleChangedEvent
import com.cloudgaming.userservice.events.UserEventProducer
import com.cloudgaming.userservice.integration.keycloak.KeycloakAdminClient
import com.cloudgaming.userservice.persistence.UserRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleManagementServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var keycloakAdminClient: KeycloakAdminClient

    @Mock
    private lateinit var userEventProducer: UserEventProducer

    @Mock
    private lateinit var securityUtils: SecurityUtils

    private lateinit var roleManagementService: RoleManagementService

    private val targetUserId = UUID.randomUUID()
    private val adminUserId = UUID.randomUUID()
    private val targetKeycloakId = "keycloak-user-123"

    @BeforeEach
    fun setUp() {
        roleManagementService = RoleManagementService(
            userRepository,
            keycloakAdminClient,
            userEventProducer,
            securityUtils
        )

        whenever(securityUtils.getCurrentUserId()).thenReturn(adminUserId)
    }

    private fun testUser(id: UUID = targetUserId, keycloakId: String = targetKeycloakId): User {
        return User(
            id = id,
            keycloakId = keycloakId,
            email = "test@example.com",
            displayName = "Test User",
            avatarUrl = "https://example.com/avatar.png",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            lastLoginAt = Instant.parse("2026-06-19T12:00:00Z")
        )
    }

    @Nested
    inner class FindById {

        @Test
        fun `findById for existing user returns User`() {
            val user = testUser()
            whenever(userRepository.findById(targetUserId)).thenReturn(Optional.of(user))

            val result = roleManagementService.findById(targetUserId)

            Assertions.assertThat(result).isNotNull
            Assertions.assertThat(result!!.id).isEqualTo(targetUserId)
            Assertions.assertThat(result.keycloakId).isEqualTo(targetKeycloakId)
        }

        @Test
        fun `findById for nonexistent user returns null`() {
            whenever(userRepository.findById(targetUserId)).thenReturn(Optional.empty())

            val result = roleManagementService.findById(targetUserId)

            Assertions.assertThat(result).isNull()
        }
    }

    @Nested
    inner class FindByKeycloakId {

        @Test
        fun `findByKeycloakId for existing user returns User`() {
            val user = testUser()
            whenever(userRepository.findByKeycloakId(targetKeycloakId)).thenReturn(user)

            val result = roleManagementService.findByKeycloakId(targetKeycloakId)

            Assertions.assertThat(result).isNotNull
            Assertions.assertThat(result!!.keycloakId).isEqualTo(targetKeycloakId)
        }

        @Test
        fun `findByKeycloakId for nonexistent user returns null`() {
            whenever(userRepository.findByKeycloakId(targetKeycloakId)).thenReturn(null)

            val result = roleManagementService.findByKeycloakId(targetKeycloakId)

            Assertions.assertThat(result).isNull()
        }
    }

    @Nested
    inner class AssignRole {

        @Test
        fun `assignRole for existing user calls keycloakAdminClient assignRole and publishes event with action=ADD`() {
            val user = testUser()
            val expectedEvent = UserRoleChangedEvent(
                targetUserId = targetUserId,
                performedByAdminId = adminUserId,
                role = "PREMIUM",
                action = "ADD"
            )

            whenever(userRepository.findById(targetUserId)).thenReturn(Optional.of(user))

            val result = roleManagementService.assignRole(targetUserId, Role.PREMIUM, adminUserId)

            Assertions.assertThat(result.targetUserId).isEqualTo(expectedEvent.targetUserId)
            Assertions.assertThat(result.performedByAdminId).isEqualTo(expectedEvent.performedByAdminId)
            Assertions.assertThat(result.role).isEqualTo(expectedEvent.role)
            Assertions.assertThat(result.action).isEqualTo(expectedEvent.action)

            verify(keycloakAdminClient).assignRole(targetKeycloakId, "PREMIUM")
            verify(userEventProducer).publishUserRoleChanged(
                targetUserId = targetUserId,
                performedByAdminId = adminUserId,
                role = "PREMIUM",
                action = "ADD"
            )
        }

        @Test
        fun `assignRole for nonexistent userId throws UserNotFoundException`() {
            whenever(userRepository.findById(targetUserId)).thenReturn(Optional.empty())

            Assertions.assertThatThrownBy {
                roleManagementService.assignRole(targetUserId, Role.PREMIUM, adminUserId)
            }.isInstanceOf(UserNotFoundException::class.java)

            verify(keycloakAdminClient, never()).assignRole(any(), any())
            verify(userEventProducer, never()).publishUserRoleChanged(any(), any(), any(), any())
        }

        @Test
        fun `assignRole with Role INTERNAL throws IllegalArgumentException`() {
            val user = testUser()
            whenever(userRepository.findById(targetUserId)).thenReturn(Optional.of(user))

            Assertions.assertThatThrownBy {
                roleManagementService.assignRole(targetUserId, Role.INTERNAL, adminUserId)
            }.isInstanceOf(IllegalArgumentException::class.java)

            verify(keycloakAdminClient, never()).assignRole(any(), any())
            verify(userEventProducer, never()).publishUserRoleChanged(any(), any(), any(), any())
        }

        @Test
        fun `assignRole when KeycloakApiException occurs propagates the exception`() {
            val user = testUser()
            whenever(userRepository.findById(targetUserId)).thenReturn(Optional.of(user))
            whenever(keycloakAdminClient.assignRole(targetKeycloakId, "PREMIUM"))
                .thenThrow(KeycloakApiException("Connection refused"))

            Assertions.assertThatThrownBy {
                roleManagementService.assignRole(targetUserId, Role.PREMIUM, adminUserId)
            }.isInstanceOf(KeycloakApiException::class.java)

            verify(userEventProducer, never()).publishUserRoleChanged(any(), any(), any(), any())
        }
    }

    @Nested
    inner class RemoveRole {

        @Test
        fun `removeRole for existing user calls keycloakAdminClient removeRole and publishes event with action=REMOVE`() {
            val user = testUser()
            val expectedEvent = UserRoleChangedEvent(
                targetUserId = targetUserId,
                performedByAdminId = adminUserId,
                role = "PREMIUM",
                action = "REMOVE"
            )

            whenever(userRepository.findById(targetUserId)).thenReturn(Optional.of(user))

            val result = roleManagementService.removeRole(targetUserId, Role.PREMIUM, adminUserId)

            Assertions.assertThat(result.targetUserId).isEqualTo(expectedEvent.targetUserId)
            Assertions.assertThat(result.performedByAdminId).isEqualTo(expectedEvent.performedByAdminId)
            Assertions.assertThat(result.role).isEqualTo(expectedEvent.role)
            Assertions.assertThat(result.action).isEqualTo(expectedEvent.action)

            verify(keycloakAdminClient).removeRole(targetKeycloakId, "PREMIUM")
            verify(userEventProducer).publishUserRoleChanged(
                targetUserId = targetUserId,
                performedByAdminId = adminUserId,
                role = "PREMIUM",
                action = "REMOVE"
            )
        }

        @Test
        fun `removeRole for nonexistent userId throws UserNotFoundException`() {
            whenever(userRepository.findById(targetUserId)).thenReturn(Optional.empty())

            Assertions.assertThatThrownBy {
                roleManagementService.removeRole(targetUserId, Role.PREMIUM, adminUserId)
            }.isInstanceOf(UserNotFoundException::class.java)

            verify(keycloakAdminClient, never()).removeRole(any(), any())
            verify(userEventProducer, never()).publishUserRoleChanged(any(), any(), any(), any())
        }

        @Test
        fun `removeRole with Role INTERNAL throws IllegalArgumentException`() {
            val user = testUser()
            whenever(userRepository.findById(targetUserId)).thenReturn(Optional.of(user))

            Assertions.assertThatThrownBy {
                roleManagementService.removeRole(targetUserId, Role.INTERNAL, adminUserId)
            }.isInstanceOf(IllegalArgumentException::class.java)

            verify(keycloakAdminClient, never()).removeRole(any(), any())
            verify(userEventProducer, never()).publishUserRoleChanged(any(), any(), any(), any())
        }

        @Test
        fun `removeRole when KeycloakApiException occurs propagates the exception`() {
            val user = testUser()
            whenever(userRepository.findById(targetUserId)).thenReturn(Optional.of(user))
            whenever(keycloakAdminClient.removeRole(targetKeycloakId, "PREMIUM"))
                .thenThrow(KeycloakApiException("Connection refused"))

            Assertions.assertThatThrownBy {
                roleManagementService.removeRole(targetUserId, Role.PREMIUM, adminUserId)
            }.isInstanceOf(KeycloakApiException::class.java)

            verify(userEventProducer, never()).publishUserRoleChanged(any(), any(), any(), any())
        }
    }

    @Nested
    inner class EventPublication {

        @Test
        fun `after a successful assignRole publishUserRoleChanged is called with the correct arguments`() {
            val user = testUser()
            whenever(userRepository.findById(targetUserId)).thenReturn(Optional.of(user))

            roleManagementService.assignRole(targetUserId, Role.PREMIUM, adminUserId)

            val captorTargetUserId = argumentCaptor<UUID>()
            val captorAdminId = argumentCaptor<UUID>()
            val captorRole = argumentCaptor<String>()
            val captorAction = argumentCaptor<String>()

            verify(userEventProducer).publishUserRoleChanged(
                captorTargetUserId.capture(),
                captorAdminId.capture(),
                captorRole.capture(),
                captorAction.capture()
            )

            Assertions.assertThat(captorTargetUserId.firstValue).isEqualTo(targetUserId)
            Assertions.assertThat(captorAdminId.firstValue).isEqualTo(adminUserId)
            Assertions.assertThat(captorRole.firstValue).isEqualTo("PREMIUM")
            Assertions.assertThat(captorAction.firstValue).isEqualTo("ADD")
        }

        @Test
        fun `after a successful removeRole publishUserRoleChanged is called with the correct arguments`() {
            val user = testUser()
            whenever(userRepository.findById(targetUserId)).thenReturn(Optional.of(user))

            roleManagementService.removeRole(targetUserId, Role.PREMIUM, adminUserId)

            val captorTargetUserId = argumentCaptor<UUID>()
            val captorAdminId = argumentCaptor<UUID>()
            val captorRole = argumentCaptor<String>()
            val captorAction = argumentCaptor<String>()

            verify(userEventProducer).publishUserRoleChanged(
                captorTargetUserId.capture(),
                captorAdminId.capture(),
                captorRole.capture(),
                captorAction.capture()
            )

            Assertions.assertThat(captorTargetUserId.firstValue).isEqualTo(targetUserId)
            Assertions.assertThat(captorAdminId.firstValue).isEqualTo(adminUserId)
            Assertions.assertThat(captorRole.firstValue).isEqualTo("PREMIUM")
            Assertions.assertThat(captorAction.firstValue).isEqualTo("REMOVE")
        }
    }
}