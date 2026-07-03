package com.cloudgaming.userservice.integration.keycloak

import com.cloudgaming.userservice.constants.JwtClaim
import com.cloudgaming.userservice.constants.Role
import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
class KeycloakRoleConverter : Converter<Jwt, Collection<GrantedAuthority>> {

    override fun convert(jwt: Jwt): Collection<GrantedAuthority> {
        val realmAccess = jwt.claims[JwtClaim.REALM_ACCESS.claimName] as? Map<*, *>
            ?: return emptyList()

        val roles = (realmAccess[JwtClaim.ROLES.claimName] as? List<*>)
            ?.filterIsInstance<String>()
            ?: return emptyList()

        return roles
            .asSequence()
            .mapNotNull { Role.fromRoleName(it) }
            .filter { it in Role.keycloakRoles }
            .map { SimpleGrantedAuthority(it.authority) }
            .toList()
    }

}