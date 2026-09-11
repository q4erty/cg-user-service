package com.cloudgaming.userservice.dto

import com.cloudgaming.userservice.domain.TransactionType
import java.math.BigDecimal

internal data class CachedIdempotentResult(
    val type: TransactionType,
    val amount: BigDecimal,
    val response: BalanceOperationResponse
)
