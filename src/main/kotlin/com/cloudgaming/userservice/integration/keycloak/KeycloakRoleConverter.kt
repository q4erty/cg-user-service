package com.cloudgaming.userservice.integration.keycloak

import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class KeycloakRoleConverter : Converter<Jwt, Collection<GrantedAuthority>> {

    private val allowedRoles: Set<String> = setOf("PLAYER", "PREMIUM", "ADMIN")

    override fun convert(jwt: Jwt): Collection<GrantedAuthority> {
        val realmAccess = jwt.claims["realm_access"] as? Map<*, *>
            ?: return emptyList()

        val roles = (realmAccess["roles"] as? List<*>)
            ?.filterIsInstance<String>()
            ?: return emptyList()

        return roles
            .asSequence()
            .map { it.uppercase(Locale.ROOT) }
            .filter { it in allowedRoles }
            .map { SimpleGrantedAuthority("ROLE_$it") }
            .toList()
    }
}