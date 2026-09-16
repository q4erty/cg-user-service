package com.cloudgaming.userservice.dto.admin

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class BalanceAdjustRequest(
    @field:JsonProperty("amount")
    @field:NotNull(message = "Amount is required")
    @field:DecimalMin(value = "-1000000.00", message = "Amount must be at least -1000000.00")
    @field:DecimalMax(value = "1000000.00", message = "Amount must be at most 1000000.00")
    val amount: BigDecimal,

    @field:JsonProperty("description")
    @field:NotBlank(message = "Description is required")
    @field:Size(min = 10, max = 500, message = "Description must be between 10 and 500 characters")
    val description: String
) {
    init {
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            throw IllegalArgumentException("Amount cannot be zero")
        }
    }
}