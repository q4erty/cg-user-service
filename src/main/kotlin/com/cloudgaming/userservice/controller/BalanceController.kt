package com.cloudgaming.userservice.controller

import com.cloudgaming.userservice.common.security.SecurityUtils
import com.cloudgaming.userservice.dto.BalanceDto
import com.cloudgaming.userservice.dto.TransactionDto
import com.cloudgaming.userservice.service.BalanceQueryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users/me/balance")
@Tag(name = "Balance", description = "Current user balance and transaction history")
@SecurityRequirement(name = "bearerAuth")
class BalanceController(
    private val balanceQueryService: BalanceQueryService,
    private val securityUtils: SecurityUtils
) {

    @GetMapping
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    @Operation(summary = "Get current balance", description = "Returns amount, currency and last operation time")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Balance returned"),
        ApiResponse(responseCode = "401", description = "Not authenticated"),
        ApiResponse(responseCode = "403", description = "Missing PLAYER role"),
        ApiResponse(responseCode = "404", description = "User not found")
    )
    fun getBalance(): ResponseEntity<BalanceDto> {
        val userId = securityUtils.getCurrentUserId()
        return ResponseEntity.ok(balanceQueryService.getBalance(userId))
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    @Operation(
        summary = "Get transaction history",
        description = "Returns a paginated list of the current user's transactions, newest first by default"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Transactions returned"),
        ApiResponse(responseCode = "400", description = "Invalid page, size or sort parameter"),
        ApiResponse(responseCode = "401", description = "Not authenticated"),
        ApiResponse(responseCode = "403", description = "Missing PLAYER role")
    )
    fun getTransactionHistory(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "createdAt,desc") sort: String
    ): ResponseEntity<Page<TransactionDto>> {
        val userId = securityUtils.getCurrentUserId()
        return ResponseEntity.ok(balanceQueryService.getTransactionHistory(userId, page, size, sort))
    }

    @GetMapping("/transactions/{id}")
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    @Operation(summary = "Get transaction by id", description = "Returns a single transaction owned by the current user")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Transaction returned"),
        ApiResponse(responseCode = "401", description = "Not authenticated"),
        ApiResponse(responseCode = "403", description = "Missing PLAYER role"),
        ApiResponse(responseCode = "404", description = "Transaction not found")
    )
    fun getTransactionById(@PathVariable id: UUID): ResponseEntity<TransactionDto> {
        val userId = securityUtils.getCurrentUserId()
        return ResponseEntity.ok(balanceQueryService.getTransactionById(userId, id))
    }
}
