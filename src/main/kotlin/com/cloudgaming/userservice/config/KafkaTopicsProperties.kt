package com.cloudgaming.userservice.config

import com.cloudgaming.userservice.constants.KafkaTopics
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.kafka.topics")
data class KafkaTopicsProperties(
    val userEvents: String = KafkaTopics.USER_EVENTS,
    val paymentTransactions: String = KafkaTopics.PAYMENT_TRANSACTIONS,
    val sessionEvents: String = KafkaTopics.SESSION_EVENTS
)
