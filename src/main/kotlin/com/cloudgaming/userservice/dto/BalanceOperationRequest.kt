package com.cloudgaming.userservice.dto

import com.cloudgaming.userservice.domain.TransactionType
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

data class BalanceOperationRequest(

    @field:NotNull
    @JsonProperty("type")
    val type: TransactionType,

    @field:NotNull
    @field:DecimalMin(value = "0.01")
    @JsonProperty("amount")
    val amount: BigDecimal,

    @field:NotBlank
    @field:Size(max = 255)
    @JsonProperty("idempotency_key")
    val idempotencyKey: String,

    @JsonProperty("session_id")
    val sessionId: UUID? = null,

    @JsonProperty("payment_id")
    val paymentId: UUID? = null,

    @field:Size(max = 500)
    @JsonProperty("description")
    val description: String? = null
) {

    @get:AssertTrue(message = "session_id is required for SESSION_DEBIT and REFUND operations")
    val isSessionIdPresentWhenRequired: Boolean
        get() = if (type == TransactionType.SESSION_DEBIT || type == TransactionType.REFUND) sessionId != null else true

    @get:AssertTrue(message = "payment_id is required for DEPOSIT operations")
    val isPaymentIdPresentWhenRequired: Boolean
        get() = if (type == TransactionType.DEPOSIT) paymentId != null else true
}
