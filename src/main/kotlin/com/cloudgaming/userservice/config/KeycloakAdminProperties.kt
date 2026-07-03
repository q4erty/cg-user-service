package com.cloudgaming.userservice.config

import com.cloudgaming.userservice.constants.KeycloakDefaults
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "keycloak.admin")
data class KeycloakAdminProperties(
    val serverUrl: String = "",
    val realm: String = KeycloakDefaults.DEFAULT_REALM,
    val clientId: String = "",
    val clientSecret: String = ""
)
