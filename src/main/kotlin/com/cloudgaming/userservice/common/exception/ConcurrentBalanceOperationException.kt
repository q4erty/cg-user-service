package com.cloudgaming.userservice.common.exception

import java.util.UUID

class ConcurrentBalanceOperationException(
    val userId: UUID,
    message: String = "Cannot acquire lock for user $userId within timeout"
) : RuntimeException(message)
