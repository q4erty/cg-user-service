package com.cloudgaming.userservice.common.exception

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Unified Error Response Format (Simplified RFC 7807).
 *
 * ```json
 * {
 * "error": "USER_NOT_FOUND",
 * "message": "User with id=123 not found",
 * "timestamp": "2026-06-19T12:34:56.789Z",
 * "path": "/api/v1/users/me",
 * "details": { "userId": "123" }
 * }
 * ```
 *
 * @property {string} error - Machine-readable error code (SCREAMING_SNAKE_CASE).
 * @property {string} message - Human-readable description for end-users.
 * @property {object} [details] - Optional context for debugging.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(

    @JsonProperty("error")
    val error: String,

    @JsonProperty("message")
    val message: String,

    @JsonProperty("timestamp")
    val timestamp: Instant = Instant.now(),

    @JsonProperty("path")
    val path: String? = null,

    @JsonProperty("details")
    val details: Map<String, Any>? = null
)
