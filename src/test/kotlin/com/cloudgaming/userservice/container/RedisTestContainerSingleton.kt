package com.cloudgaming.userservice.container

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

object RedisTestContainerSingleton {
    const val PASSWORD = "test_password"

    val instance: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379)
        .withCommand("redis-server", "--requirepass", PASSWORD)
        .also { it.start() }
}
