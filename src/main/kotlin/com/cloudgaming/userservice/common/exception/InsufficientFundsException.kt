package com.cloudgaming.userservice.common.exception

import java.math.BigDecimal
import java.util.UUID

class InsufficientFundsException(
    val userId: UUID,
    val currentBalance: BigDecimal,
    val requestedAmount: BigDecimal
) : RuntimeException(
    "User $userId has $currentBalance but $requestedAmount was requested"
)
