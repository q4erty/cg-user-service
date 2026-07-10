package com.cloudgaming.userservice.constants

import java.time.Duration

object RedisKey {
    const val EXISTS_PREFIX = "user:exists:"
    const val ID_MAPPING_PREFIX = "user:id:"
    val EXISTS_TTL: Duration = Duration.ofHours(24)
    val ID_MAPPING_TTL: Duration = Duration.ofHours(1)
}