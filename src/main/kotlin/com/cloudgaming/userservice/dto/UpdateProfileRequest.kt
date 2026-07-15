package com.cloudgaming.userservice.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.URL

data class UpdateProfileRequest(

    @field:NotBlank(message = "Display name must not be blank")
    @field:Size(max = 255, message = "Display name must be at most 255 characters")
    @JsonProperty("display_name")
    val displayName: String,

    @field:URL(message = "Avatar URL must be a valid URL")
    @field:Size(max = 1024, message = "Avatar URL must be at most 1024 characters")
    @JsonProperty("avatar_url")
    val avatarUrl: String? = null
)
