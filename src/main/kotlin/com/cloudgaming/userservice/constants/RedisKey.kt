package com.cloudgaming.userservice.constants

import java.time.Duration

object RedisKey {
    const val EXISTS_PREFIX = "user:exists:"
    const val ID_MAPPING_PREFIX = "user:id:"
    const val BALANCE_IDEMPOTENCY_PREFIX = "balance:op:"
    const val BALANCE_LOCK_PREFIX = "lock:user:"
    const val BALANCE_PENDING_MARKER = "pending"
    val EXISTS_TTL: Duration = Duration.ofHours(24)
    val ID_MAPPING_TTL: Duration = Duration.ofHours(1)
    val BALANCE_IDEMPOTENCY_TTL: Duration = Duration.ofMinutes(5)
}