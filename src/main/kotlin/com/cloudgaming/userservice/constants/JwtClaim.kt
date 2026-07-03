package com.cloudgaming.userservice.constants

enum class JwtClaim(val claimName: String) {
    REALM_ACCESS("realm_access"),
    ROLES("roles"),
    EMAIL("email"),
    PREFERRED_USERNAME("preferred_username")
}
