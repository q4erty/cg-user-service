package com.cloudgaming.userservice.dto.admin

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class AdminUserDto(
    @JsonProperty("id")
    val id: UUID,

    @JsonProperty("keycloak_id")
    val keycloakId: String,

    @JsonProperty("email")
    val email: String,

    @JsonProperty("display_name")
    val displayName: String?,

    @JsonProperty("avatar_url")
    val avatarUrl: String?,

    @JsonProperty("balance")
    val balance: BigDecimal,

    @JsonProperty("currency")
    val currency: String,

    @JsonProperty("created_at")
    val createdAt: Instant,

    @JsonProperty("last_login_at")
    val lastLoginAt: Instant?
)