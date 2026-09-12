package com.cloudgaming.userservice.controller

import com.cloudgaming.userservice.dto.BalanceOperationRequest
import com.cloudgaming.userservice.dto.BalanceOperationResponse
import com.cloudgaming.userservice.service.BalanceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/internal/users/{userId}/balance/operations")
@Tag(name = "Internal Balance", description = "Balance mutation endpoint for trusted internal services")
class BalanceInternalController(
    private val balanceService: BalanceService
) {

    @PostMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(
        summary = "Apply a balance operation",
        description = "Applies a deposit/debit/refund/bonus/adjustment with idempotency, locking and optimistic concurrency protection"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Operation applied (or idempotent replay returned)"),
        ApiResponse(responseCode = "400", description = "Validation error"),
        ApiResponse(responseCode = "401", description = "Missing or invalid internal secret"),
        ApiResponse(responseCode = "403", description = "Missing INTERNAL role"),
        ApiResponse(responseCode = "404", description = "User not found"),
        ApiResponse(responseCode = "409", description = "Insufficient funds or idempotency conflict"),
        ApiResponse(responseCode = "503", description = "Concurrent operation in progress, retry later")
    )
    fun applyOperation(
        @PathVariable userId: UUID,
        @RequestBody @Valid request: BalanceOperationRequest
    ): ResponseEntity<BalanceOperationResponse> {
        val response = balanceService.applyOperation(userId, request)
        return ResponseEntity.ok(response)
    }
}
