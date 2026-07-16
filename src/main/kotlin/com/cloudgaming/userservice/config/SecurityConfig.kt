package com.cloudgaming.userservice.config

import com.cloudgaming.userservice.common.exception.ErrorResponse
import com.cloudgaming.userservice.common.filter.UserProvisioningFilter
import com.cloudgaming.userservice.common.security.InternalSecretFilter
import com.cloudgaming.userservice.constants.ErrorCode
import com.cloudgaming.userservice.constants.HttpDefaults
import com.cloudgaming.userservice.constants.Role
import com.cloudgaming.userservice.constants.SecurityPath
import com.cloudgaming.userservice.integration.keycloak.KeycloakRoleConverter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.boot.web.servlet.FilterRegistrationBean

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties::class)
class SecurityConfig(
    private val keycloakRoleConverter: KeycloakRoleConverter,
    private val internalSecretFilter: InternalSecretFilter,
    private val userProvisioningFilter: UserProvisioningFilter,
    private val corsProperties: CorsProperties
) {

    private val objectMapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
    }

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
            .exceptionHandling { eh ->
                eh.authenticationEntryPoint(authenticationEntryPoint())
                eh.accessDeniedHandler(accessDeniedHandler())
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2
                    .jwt { jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                    }
            }
            .addFilterBefore(internalSecretFilter, BearerTokenAuthenticationFilter::class.java)
            .addFilterAfter(userProvisioningFilter, BearerTokenAuthenticationFilter::class.java)
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
    fun authenticationEntryPoint(): AuthenticationEntryPoint {
        return AuthenticationEntryPoint { request, response, authException ->
            response.characterEncoding = HttpDefaults.CHARACTER_ENCODING
            response.contentType = HttpDefaults.CONTENT_TYPE_JSON
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            val body = ErrorResponse(
                error = ErrorCode.UNAUTHORIZED.code,
                message = authException.message ?: ErrorCode.UNAUTHORIZED.defaultMessage,
                path = request.requestURI
            )
            objectMapper.writeValue(response.outputStream, body)
        }
    }

    @Bean
    fun accessDeniedHandler(): AccessDeniedHandler {
        return AccessDeniedHandler { request, response, accessDeniedException ->
            response.characterEncoding = HttpDefaults.CHARACTER_ENCODING
            response.contentType = HttpDefaults.CONTENT_TYPE_JSON
            response.status = HttpServletResponse.SC_FORBIDDEN
            val body = ErrorResponse(
                error = ErrorCode.ACCESS_DENIED.code,
                message = accessDeniedException.message ?: ErrorCode.ACCESS_DENIED.defaultMessage,
                path = request.requestURI
            )
            objectMapper.writeValue(response.outputStream, body)
        }
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = corsProperties.allowedOrigins
            allowedMethods = corsProperties.allowedMethods
            allowedHeaders = corsProperties.allowedHeaders
            exposedHeaders = corsProperties.exposedHeaders
            allowCredentials = true
            maxAge = corsProperties.maxAge
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}