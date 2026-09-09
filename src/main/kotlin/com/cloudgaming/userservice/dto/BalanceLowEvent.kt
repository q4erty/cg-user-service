package com.cloudgaming.userservice.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class BalanceLowEvent(
    @JsonProperty("event_id") val eventId: UUID = UUID.randomUUID(),
    @JsonProperty("occurred_at") val occurredAt: Instant = Instant.now(),
    @JsonProperty("event_type") val eventType: String = "BALANCE_LOW",
    @JsonProperty("user_id") val userId: UUID,
    @JsonProperty("current_balance") val currentBalance: BigDecimal,
    @JsonProperty("threshold") val threshold: BigDecimal
)
