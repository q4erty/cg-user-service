package com.cloudgaming.userservice.dto.admin

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class RoleUpdateRequest(
    @field:JsonProperty("role")
    @field:NotBlank(message = "Role is required")
    @field:Pattern(regexp = "PLAYER|PREMIUM|ADMIN", message = "Role must be one of: PLAYER, PREMIUM, ADMIN")
    val role: String,

    @field:JsonProperty("action")
    @field:NotBlank(message = "Action is required")
    @field:Pattern(regexp = "ADD|REMOVE", message = "Action must be ADD or REMOVE")
    val action: String
)