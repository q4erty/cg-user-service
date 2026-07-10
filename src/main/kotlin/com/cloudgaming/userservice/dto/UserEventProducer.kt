package com.cloudgaming.userservice.dto

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service
import java.util.*

@Service
class UserEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    @Value("\${app.kafka.topics.user-events}") private val userEventsTopic: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun publishUserRegistered(userId: UUID, email: String, keycloakId: String) {
        val event = UserRegisteredEvent(
            userId = userId,
            email = email,
            keycloakId = keycloakId
        )

        val message: Message<UserRegisteredEvent> = MessageBuilder
            .withPayload(event)
            .setHeader(KafkaHeaders.TOPIC, userEventsTopic)
            .setHeader(KafkaHeaders.KEY, userId.toString())
            .build()

        kafkaTemplate.send(message).whenComplete { result, ex ->
            if (ex != null) {
                logger.error("Failed to publish USER_REGISTERED for user={}: {}", userId, ex.message, ex)
            } else {
                logger.info(
                    "Published USER_REGISTERED for user={} to partition={}",
                    userId, result?.recordMetadata?.partition()
                )
            }
        }
    }
}