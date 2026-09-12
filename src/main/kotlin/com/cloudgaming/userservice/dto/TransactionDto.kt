package com.cloudgaming.userservice.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TransactionDto(
    @JsonProperty("id") val id: UUID,
    @JsonProperty("amount") val amount: BigDecimal,
    @JsonProperty("type") val type: String,
    @JsonProperty("session_id") val sessionId: UUID?,
    @JsonProperty("payment_id") val paymentId: UUID?,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("description") val description: String?
)
