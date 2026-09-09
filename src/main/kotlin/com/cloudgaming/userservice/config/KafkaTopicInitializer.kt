package com.cloudgaming.userservice.config

import com.cloudgaming.userservice.constants.KafkaTopics
import jakarta.annotation.PostConstruct
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["app.kafka.auto-create-topics"], havingValue = "true", matchIfMissing = true)
class KafkaTopicInitializer(
    private val adminClient: AdminClient
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun createTopics() {
        val topics = listOf(
            NewTopic(KafkaTopics.USER_EVENTS, 3, 1),
            NewTopic(KafkaTopics.PAYMENT_TRANSACTIONS, 3, 1),
            NewTopic(KafkaTopics.SESSION_EVENTS, 3, 1)
        )

        try {
            val result = adminClient.createTopics(topics)
            result.all().get()
            topics.forEach { logger.info("Kafka topic ready: ${it.name()}") }
        } catch (e: Exception) {
            logger.debug("Topic creation skipped (probably already exist): ${e.message}")
        }
    }
}
