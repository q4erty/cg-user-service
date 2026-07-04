package com.cloudgaming.userservice.common.exception

import java.util.UUID

class UserNotFoundException private constructor(message: String) : RuntimeException(message) {

    companion object {
        fun byUserId(userId: UUID): UserNotFoundException =
            UserNotFoundException("User with id=$userId not found")

        fun byKeycloakId(keycloakId: String): UserNotFoundException =
            UserNotFoundException("User with keycloakId='$keycloakId' not found")

        operator fun invoke(message: String): UserNotFoundException =
            UserNotFoundException(message)
    }
}
