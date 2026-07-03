package com.cloudgaming.userservice.constants

enum class Role(
    val roleName: String,
    val isKeycloakManaged: Boolean = true
) {
    PLAYER("PLAYER"),
    PREMIUM("PREMIUM"),
    ADMIN("ADMIN"),
    INTERNAL("INTERNAL", isKeycloakManaged = false);

    val authority: String
        get() = "$AUTHORITY_PREFIX$roleName"

    companion object {
        const val AUTHORITY_PREFIX = "ROLE_"

        val keycloakRoles: Set<Role> = entries.filter { it.isKeycloakManaged }.toSet()

        fun fromRoleName(name: String): Role? =
            entries.find { it.roleName.equals(name, ignoreCase = true) }
    }
}
