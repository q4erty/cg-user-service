package com.cloudgaming.userservice.common.exception

import java.util.*

class TransactionNotFoundException(
    val transactionId: UUID,
    val userId: UUID
) : RuntimeException("Transaction $transactionId not found for user $userId")
