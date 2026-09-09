package com.cloudgaming.userservice.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.kafka.autoconfigure.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JacksonJsonSerializer
import tools.jackson.databind.json.JsonMapper

@Configuration
@EnableConfigurationProperties(KafkaTopicsProperties::class)
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
        kafkaProperties: KafkaProperties,
        jsonMapper: JsonMapper
    ): ProducerFactory<String, Any> {
        return DefaultKafkaProducerFactory<String, Any>(kafkaProperties.buildProducerProperties()).apply {
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
