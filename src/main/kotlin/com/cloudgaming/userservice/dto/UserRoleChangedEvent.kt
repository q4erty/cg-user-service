package com.cloudgaming.userservice.dto

import com.cloudgaming.userservice.constants.KafkaEventTypes
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

data class UserRoleChangedEvent(
    @JsonProperty("event_id") val eventId: UUID = UUID.randomUUID(),
    @JsonProperty("occurred_at") val occurredAt: Instant = Instant.now(),
    @JsonProperty("event_type") val eventType: String = KafkaEventTypes.USER_ROLE_CHANGED,
    @JsonProperty("target_user_id") val targetUserId: UUID,
    @JsonProperty("performed_by_admin_id") val performedByAdminId: UUID,
    @JsonProperty("role") val role: String,
    @JsonProperty("action") val action: String
)
