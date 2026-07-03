package com.cloudgaming.userservice.config

import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(KeycloakAdminProperties::class)
class KeycloakAdminConfig {

    @Bean(name = ["keycloak"], destroyMethod = "close")
    fun keycloakAdminClient(props: KeycloakAdminProperties): Keycloak {
        return KeycloakBuilder.builder()
            .serverUrl(props.serverUrl)
            .realm(props.realm)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(props.clientId)
            .clientSecret(props.clientSecret)
            .build()
    }

}
