package com.cloudgaming.userservice.common.exception

import java.util.UUID

class UserNotFoundException private constructor(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {

    companion object {
        fun byUserId(userId: UUID, cause: Throwable? = null): UserNotFoundException =
            UserNotFoundException("User with id=$userId not found", cause)

        fun byKeycloakId(keycloakId: String, cause: Throwable? = null): UserNotFoundException =
            UserNotFoundException("User with keycloakId='$keycloakId' not found", cause)
    }
}
