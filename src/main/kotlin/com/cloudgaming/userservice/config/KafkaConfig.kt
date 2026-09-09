package com.cloudgaming.userservice.config

import com.cloudgaming.userservice.constants.KafkaProducerDefaults
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JacksonJsonSerializer
import tools.jackson.databind.json.JsonMapper

@Configuration
class KafkaConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper::class)
    fun kafkaObjectMapper(): ObjectMapper =
        ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Bean
    @ConditionalOnMissingBean(ProducerFactory::class)
    fun producerFactory(
        jsonMapper: JsonMapper,
        @Value(KafkaProducerDefaults.BOOTSTRAP_SERVERS_PROPERTY) bootstrapServers: String
    ): ProducerFactory<String, Any> {
        val config = mutableMapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.ACKS_CONFIG to KafkaProducerDefaults.ACKS,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to KafkaProducerDefaults.ENABLE_IDEMPOTENCE,
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to KafkaProducerDefaults.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
            ProducerConfig.RETRIES_CONFIG to KafkaProducerDefaults.RETRIES,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG to KafkaProducerDefaults.DELIVERY_TIMEOUT_MS,
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG to KafkaProducerDefaults.REQUEST_TIMEOUT_MS,
            ProducerConfig.BATCH_SIZE_CONFIG to KafkaProducerDefaults.BATCH_SIZE,
            ProducerConfig.LINGER_MS_CONFIG to KafkaProducerDefaults.LINGER_MS
        )

        return DefaultKafkaProducerFactory<String, Any>(config).apply {
            keySerializer = StringSerializer()
            valueSerializer = JacksonJsonSerializer<Any>(jsonMapper)
        }
    }

    @Bean
    @ConditionalOnMissingBean(KafkaTemplate::class)
    fun kafkaTemplate(producerFactory: ProducerFactory<String, Any>): KafkaTemplate<String, Any> {
        return KafkaTemplate(producerFactory)
    }

}
