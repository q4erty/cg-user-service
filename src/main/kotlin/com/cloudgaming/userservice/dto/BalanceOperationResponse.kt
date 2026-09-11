package com.cloudgaming.userservice.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class BalanceOperationResponse(
    @JsonProperty("transaction_id")
    val transactionId: UUID,

    @JsonProperty("new_balance")
    val newBalance: BigDecimal,

    @JsonProperty("currency")
    val currency: String,

    @JsonProperty("processed_at")
    val processedAt: Instant
)
