package com.cloudgaming.userservice.container

import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

object KafkaTestContainerSingleton {
    val instance: KafkaContainer = KafkaContainer(
        DockerImageName.parse("apache/kafka-native:3.8.0")
    ).also { it.start() }
}