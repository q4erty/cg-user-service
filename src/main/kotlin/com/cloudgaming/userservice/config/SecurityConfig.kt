package com.cloudgaming.userservice.config

import com.cloudgaming.userservice.common.security.InternalSecretFilter
import com.cloudgaming.userservice.constants.Role
import com.cloudgaming.userservice.constants.SecurityPath
import com.cloudgaming.userservice.integration.keycloak.KeycloakRoleConverter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties::class)
class SecurityConfig(
    private val keycloakRoleConverter: KeycloakRoleConverter,
    private val internalSecretFilter: InternalSecretFilter,
    private val corsProperties: CorsProperties
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(SecurityPath.ACTUATOR_HEALTH.pattern, SecurityPath.ACTUATOR_INFO.pattern).permitAll()
                    .requestMatchers(SecurityPath.SWAGGER_UI.pattern, SecurityPath.API_DOCS.pattern).permitAll()
                    .requestMatchers(SecurityPath.INTERNAL_API.pattern).hasRole(Role.INTERNAL.roleName)
                    .requestMatchers(SecurityPath.USERS_ME.pattern).hasRole(Role.PLAYER.roleName)
                    .requestMatchers(SecurityPath.ADMIN_API.pattern).hasRole(Role.ADMIN.roleName)
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }
            .addFilterBefore(internalSecretFilter, BearerTokenAuthenticationFilter::class.java)
            .build()
    }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        return JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(keycloakRoleConverter)
        }
    }

    @Bean
    fun internalSecretFilterRegistration(filter: InternalSecretFilter): FilterRegistrationBean<InternalSecretFilter> {
        return FilterRegistrationBean(filter).apply { isEnabled = false }
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = corsProperties.allowedOrigins
            allowedMethods = corsProperties.allowedMethods
            allowedHeaders = corsProperties.allowedHeaders
            exposedHeaders = corsProperties.exposedHeaders
            allowCredentials = corsProperties.allowedOrigins.none { it == "*" }
            maxAge = corsProperties.maxAge
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}