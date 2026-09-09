package com.cloudgaming.userservice.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class BalanceOperationAppliedEvent(
    @JsonProperty("event_id") val eventId: UUID = UUID.randomUUID(),
    @JsonProperty("occurred_at") val occurredAt: Instant = Instant.now(),
    @JsonProperty("event_type") val eventType: String = "BALANCE_OPERATION_APPLIED",
    @JsonProperty("user_id") val userId: UUID,
    @JsonProperty("transaction_id") val transactionId: UUID,
    @JsonProperty("type") val type: String,
    @JsonProperty("amount") val amount: BigDecimal,
    @JsonProperty("new_balance") val newBalance: BigDecimal
)
