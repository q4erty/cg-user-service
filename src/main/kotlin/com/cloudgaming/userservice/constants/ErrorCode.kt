package com.cloudgaming.userservice.constants

enum class ErrorCode(val code: String, val defaultMessage: String) {
    USER_NOT_FOUND("USER_NOT_FOUND", "User not found"),
    VALIDATION_ERROR("VALIDATION_ERROR", "Request validation failed"),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "Request method not supported"),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "Content type not supported"),
    MISSING_PARAMETER("MISSING_PARAMETER", "Missing required parameter"),
    MALFORMED_JSON("MALFORMED_JSON", "Malformed JSON request"),
    NOT_FOUND("NOT_FOUND", "No endpoint found"),
    ROLE_NOT_FOUND("ROLE_NOT_FOUND", "Role not found"),
    ACCESS_DENIED("ACCESS_DENIED", "You don't have permission to access this resource"),
    UNAUTHORIZED("UNAUTHORIZED", "Authentication required or token invalid"),
    INSUFFICIENT_FUNDS("INSUFFICIENT_FUNDS", "Insufficient funds"),
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "Idempotency conflict"),
    CONCURRENT_OPERATION("CONCURRENT_OPERATION", "Concurrent operation in progress, retry later"),
    KEYCLOAK_UNAVAILABLE("KEYCLOAK_UNAVAILABLE", "Identity provider is temporarily unavailable"),
    INTERNAL_ERROR("INTERNAL_ERROR", "An unexpected error occurred")
}