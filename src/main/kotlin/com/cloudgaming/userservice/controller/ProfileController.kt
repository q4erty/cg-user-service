package com.cloudgaming.userservice.controller

import com.cloudgaming.userservice.common.security.SecurityUtils
import com.cloudgaming.userservice.dto.UpdateProfileRequest
import com.cloudgaming.userservice.dto.UserProfileDto
import com.cloudgaming.userservice.service.ProfileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Profile", description = "Current user profile")
@SecurityRequirement(name = "bearerAuth")
class ProfileController(
    private val profileService: ProfileService,
    private val securityUtils: SecurityUtils
) {

    @GetMapping
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    @Operation(summary = "Get current user profile", description = "Returns profile with current balance")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Profile returned"),
        ApiResponse(responseCode = "401", description = "Not authenticated"),
        ApiResponse(responseCode = "403", description = "Missing PLAYER role"),
        ApiResponse(responseCode = "404", description = "User not found in DB")
    )
    fun getCurrentUser(): ResponseEntity<UserProfileDto> {
        val userId = securityUtils.getCurrentUserId()
        val profile = profileService.getProfile(userId)
        return ResponseEntity.ok(profile)
    }

    @PatchMapping
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    @Operation(summary = "Update profile", description = "Updates displayName and avatarUrl")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Profile updated"),
        ApiResponse(responseCode = "400", description = "Validation error"),
        ApiResponse(responseCode = "401", description = "Not authenticated"),
        ApiResponse(responseCode = "403", description = "Missing PLAYER role"),
        ApiResponse(responseCode = "404", description = "User not found")
    )
    fun updateProfile(
        @RequestBody @Valid request: UpdateProfileRequest
    ): ResponseEntity<UserProfileDto> {
        val userId = securityUtils.getCurrentUserId()
        val updated = profileService.updateProfile(userId, request)
        return ResponseEntity.ok(updated)
    }
}