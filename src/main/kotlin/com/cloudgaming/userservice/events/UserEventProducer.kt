package com.cloudgaming.userservice.events

import com.cloudgaming.userservice.config.KafkaTopicsProperties
import com.cloudgaming.userservice.constants.HeaderNames
import com.cloudgaming.userservice.constants.KafkaEventTypes
import com.cloudgaming.userservice.domain.TransactionType
import com.cloudgaming.userservice.dto.BalanceLowEvent
import com.cloudgaming.userservice.dto.BalanceOperationAppliedEvent
import com.cloudgaming.userservice.dto.UserRegisteredEvent
import com.cloudgaming.userservice.dto.UserRoleChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.util.*

@Service
class UserEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val topics: KafkaTopicsProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun publishUserRegistered(userId: UUID, email: String, keycloakId: String) {
        val event = UserRegisteredEvent(
            userId = userId,
            email = email,
            keycloakId = keycloakId
        )
        sendEvent(
            topic = topics.userEvents,
            key = userId.toString(),
            event = event,
            eventName = KafkaEventTypes.USER_REGISTERED
        )
    }

    fun publishUserRoleChanged(
        targetUserId: UUID,
        performedByAdminId: UUID,
        role: String,
        action: String
    ) {
        val event = UserRoleChangedEvent(
            targetUserId = targetUserId,
            performedByAdminId = performedByAdminId,
            role = role,
            action = action
        )
        sendEvent(
            topic = topics.userEvents,
            key = targetUserId.toString(),
            event = event,
            eventName = KafkaEventTypes.USER_ROLE_CHANGED
        )
    }

    fun publishBalanceLow(userId: UUID, currentBalance: BigDecimal, threshold: BigDecimal) {
        val event = BalanceLowEvent(
            userId = userId,
            currentBalance = currentBalance,
            threshold = threshold
        )
        sendEvent(
            topic = topics.userEvents,
            key = userId.toString(),
            event = event,
            eventName = KafkaEventTypes.BALANCE_LOW
        )
    }

    fun publishBalanceOperationApplied(
        userId: UUID,
        transactionId: UUID,
        type: String,
        amount: BigDecimal,
        newBalance: BigDecimal
    ) {
        val event = BalanceOperationAppliedEvent(
            userId = userId,
            transactionId = transactionId,
            type = type,
            amount = amount,
            newBalance = newBalance
        )

        val topic = when (type) {
            TransactionType.DEPOSIT.name -> topics.paymentTransactions
            TransactionType.SESSION_DEBIT.name, TransactionType.REFUND.name -> topics.sessionEvents
            else -> topics.userEvents
        }

        sendEvent(
            topic = topic,
            key = userId.toString(),
            event = event,
            eventName = KafkaEventTypes.BALANCE_OPERATION_APPLIED
        )
    }

    private fun sendEvent(
        topic: String,
        key: String,
        event: Any,
        eventName: String
    ) {
        val message: Message<Any> = MessageBuilder
            .withPayload(event)
            .setHeader(KafkaHeaders.TOPIC, topic)
            .setHeader(KafkaHeaders.KEY, key)
            .setHeader(HeaderNames.EVENT_TYPE, eventName)
            .build()

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    doSend(message, topic, eventName)
                }
            })
        } else {
            doSend(message, topic, eventName)
        }
    }

    private fun doSend(message: Message<Any>, topic: String, eventName: String) {
        kafkaTemplate.send(message).whenComplete { result, ex ->
            if (ex != null) {
                logger.error(
                    "Failed to publish {} to topic={}: {}",
                    eventName, topic, ex.message, ex
                )
            } else {
                logger.info(
                    "Published {} to topic={}, partition={}, offset={}",
                    eventName, topic,
                    result?.recordMetadata?.partition(),
                    result?.recordMetadata?.offset()
                )
            }
        }
    }
}
