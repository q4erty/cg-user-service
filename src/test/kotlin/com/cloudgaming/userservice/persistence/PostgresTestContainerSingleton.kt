package com.cloudgaming.userservice.persistence

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

object PostgresTestContainerSingleton {
    val instance: PostgreSQLContainer<*> = PostgreSQLContainer(
        DockerImageName.parse("postgres:16-alpine")
    )
        .withDatabaseName("cloud_gaming_db")
        .withUsername("test_user")
        .withPassword("test_password")

    init {
        instance.start()
    }
}