package com.cloudgaming.userservice.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UserProfileDto(
    @JsonProperty("id") val id: UUID,
    @JsonProperty("email") val email: String,
    @JsonProperty("display_name") val displayName: String?,
    @JsonProperty("avatar_url") val avatarUrl: String?,
    @JsonProperty("balance") val balance: BigDecimal,
    @JsonProperty("currency") val currency: String,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("last_login_at") val lastLoginAt: Instant?
)
