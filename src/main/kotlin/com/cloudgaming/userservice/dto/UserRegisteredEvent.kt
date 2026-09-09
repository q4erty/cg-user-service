package com.cloudgaming.userservice.dto

import com.cloudgaming.userservice.constants.KafkaEventTypes
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.*

data class UserRegisteredEvent(
    @JsonProperty("event_id") val eventId: UUID = UUID.randomUUID(),
    @JsonProperty("occurred_at") val occurredAt: Instant = Instant.now(),
    @JsonProperty("event_type") val eventType: String = KafkaEventTypes.USER_REGISTERED,
    @JsonProperty("user_id") val userId: UUID,
    @JsonProperty("email") val email: String,
    @JsonProperty("keycloak_id") val keycloakId: String
)
