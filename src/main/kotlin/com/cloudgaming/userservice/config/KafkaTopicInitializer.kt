package com.cloudgaming.userservice.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
@ConditionalOnProperty(name = ["app.kafka.auto-create-topics"], havingValue = "true", matchIfMissing = true)
class KafkaTopicInitializer(private val topics: KafkaTopicsProperties) {

    @Bean
    fun userEventsTopic(): NewTopic =
        TopicBuilder.name(topics.userEvents).partitions(3).replicas(1).build()

    @Bean
    fun paymentTransactionsTopic(): NewTopic =
        TopicBuilder.name(topics.paymentTransactions).partitions(3).replicas(1).build()

    @Bean
    fun sessionEventsTopic(): NewTopic =
        TopicBuilder.name(topics.sessionEvents).partitions(3).replicas(1).build()
}
