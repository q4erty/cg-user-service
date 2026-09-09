package com.cloudgaming.userservice.constants

object KafkaProducerDefaults {
    const val BOOTSTRAP_SERVERS_PROPERTY = $$"${spring.kafka.bootstrap-servers}"
    const val ACKS = "all"
    const val ENABLE_IDEMPOTENCE = true
    const val MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION = 5
    const val RETRIES = Int.MAX_VALUE
    const val DELIVERY_TIMEOUT_MS = 120_000
    const val REQUEST_TIMEOUT_MS = 30_000
    const val BATCH_SIZE = 16_384
    const val LINGER_MS = 5
}
