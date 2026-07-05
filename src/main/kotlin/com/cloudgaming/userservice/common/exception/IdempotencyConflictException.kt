package com.cloudgaming.userservice.common.exception

import java.util.UUID

class IdempotencyConflictException(
    val idempotencyKey: String,
    val previousTransactionId: UUID
) : RuntimeException(
    "Idempotency key '$idempotencyKey' was already used for a different operation " +
            "(previous transaction: $previousTransactionId)"
)
