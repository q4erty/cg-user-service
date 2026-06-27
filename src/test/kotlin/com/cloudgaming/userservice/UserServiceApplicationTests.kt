package com.cloudgaming.userservice

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@Disabled("Requires full infrastructure: Redis, Kafka, Keycloak, DB")
class UserServiceApplicationTests {

    @Test
    fun contextLoads() {
    }

}
