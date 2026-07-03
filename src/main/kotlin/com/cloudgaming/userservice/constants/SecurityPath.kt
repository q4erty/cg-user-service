package com.cloudgaming.userservice.constants

enum class SecurityPath(val pattern: String, val requiredRole: Role? = null) {
    ACTUATOR_HEALTH("/actuator/health"),
    ACTUATOR_INFO("/actuator/info"),
    SWAGGER_UI("/swagger-ui/**"),
    API_DOCS("/v3/api-docs/**"),
    INTERNAL_API("${ApiPaths.INTERNAL_PREFIX}**", Role.INTERNAL),
    USERS_ME("/api/v1/users/me/**", Role.PLAYER),
    ADMIN_API("/api/v1/admin/**", Role.ADMIN)
}