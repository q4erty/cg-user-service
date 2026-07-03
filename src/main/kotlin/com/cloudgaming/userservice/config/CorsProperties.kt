package com.cloudgaming.userservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = listOf("http://localhost:3000", "http://localhost:8080"),
    val allowedMethods: List<String> = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
    val allowedHeaders: List<String> = listOf("*"),
    val exposedHeaders: List<String> = listOf("X-Request-Id", "X-Internal-Secret"),
    val maxAge: Long = 3600L
)