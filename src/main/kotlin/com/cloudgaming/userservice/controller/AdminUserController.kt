package com.cloudgaming.userservice.controller

import com.cloudgaming.userservice.service.RoleManagementService
import com.cloudgaming.userservice.common.exception.UserNotFoundException
import com.cloudgaming.userservice.constants.AdminConstants
import com.cloudgaming.userservice.constants.Role
import com.cloudgaming.userservice.common.exception.RoleNotFoundException
import com.cloudgaming.userservice.dto.BalanceOperationRequest
import com.cloudgaming.userservice.dto.BalanceOperationResponse
import com.cloudgaming.userservice.dto.TransactionDto
import com.cloudgaming.userservice.domain.TransactionType
import com.cloudgaming.userservice.persistence.BalanceTransactionRepository
import com.cloudgaming.userservice.persistence.UserBalanceRepository
import com.cloudgaming.userservice.persistence.UserRepository
import com.cloudgaming.userservice.service.BalanceService
import com.cloudgaming.userservice.common.security.SecurityUtils
import com.cloudgaming.userservice.dto.admin.AdminUserDto
import com.cloudgaming.userservice.dto.admin.BalanceAdjustRequest
import com.cloudgaming.userservice.dto.admin.RoleUpdateRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin User Management", description = "Admin endpoints for user management")
@SecurityRequirement(name = "bearerAuth")
class AdminUserController(
    private val roleManagementService: RoleManagementService,
    private val userRepository: UserRepository,
    private val userBalanceRepository: UserBalanceRepository,
    private val balanceTransactionRepository: BalanceTransactionRepository,
    private val balanceService: BalanceService,
    private val securityUtils: SecurityUtils
) {

    @PatchMapping("/{id}/role")
    @Operation(summary = "Assign or remove user role")
    fun updateRole(
        @PathVariable id: UUID,
        @RequestBody @Valid request: RoleUpdateRequest
    ): ResponseEntity<Void> {
        val adminId = securityUtils.getCurrentUserId()
        val role = Role.fromRoleName(request.role)
            ?: throw RoleNotFoundException("Role '${request.role}' is not a valid role")

        when (request.action.uppercase()) {
            AdminConstants.ACTION_ADD -> roleManagementService.assignRole(id, role, adminId)
            AdminConstants.ACTION_REMOVE -> roleManagementService.removeRole(id, role, adminId)
            else -> throw RoleNotFoundException("Invalid action: ${request.action}")
        }

        return ResponseEntity.noContent().build()
    }

    @GetMapping
    @Operation(summary = "List users with pagination and email filter")
    fun listUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") @Max(100) size: Int,
        @RequestParam(defaultValue = "") email: String,
        @RequestParam(defaultValue = AdminConstants.DEFAULT_SORT) sort: String
    ): ResponseEntity<Page<AdminUserDto>> {
        val sortSpec = parseSort(sort)
        val pageable = PageRequest.of(page, size, sortSpec)

        val users = if (email.isBlank()) {
            userRepository.findAll(pageable)
        } else {
            userRepository.findByEmailContainingIgnoreCase(email, pageable)
        }

        val userDtos = users.map { user ->
            val balance = userBalanceRepository.findByUserId(user.id)
            AdminUserDto(
                id = user.id,
                keycloakId = user.keycloakId,
                email = user.email,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl,
                balance = balance?.amount ?: BigDecimal.ZERO,
                currency = balance?.currency ?: AdminConstants.DEFAULT_CURRENCY,
                createdAt = user.createdAt,
                lastLoginAt = user.lastLoginAt
            )
        }

        return ResponseEntity.ok(userDtos)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user details by ID")
    fun getUserById(@PathVariable id: UUID): ResponseEntity<AdminUserDto> {
        val user = userRepository.findById(id).orElseThrow { UserNotFoundException.byUserId(id) }
        val balance = userBalanceRepository.findByUserId(id)
            ?: throw UserNotFoundException.byUserId(id)

        val dto = AdminUserDto(
            id = user.id,
            keycloakId = user.keycloakId,
            email = user.email,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
            balance = balance.amount,
            currency = balance.currency,
            createdAt = user.createdAt,
            lastLoginAt = user.lastLoginAt
        )

        return ResponseEntity.ok(dto)
    }

    @GetMapping("/{id}/balance/transactions")
    @Operation(summary = "Get user transaction history (admin)")
    fun getTransactions(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = AdminConstants.DEFAULT_SORT) sort: String
    ): ResponseEntity<Page<TransactionDto>> {
        if (!userRepository.existsById(id)) {
            throw UserNotFoundException.byUserId(id)
        }

        val sortSpec = parseSort(sort)
        val pageable = PageRequest.of(page, size, sortSpec)
        val transactions = balanceTransactionRepository.findByUserIdOrderByCreatedAtDesc(id, pageable)

        return ResponseEntity.ok(transactions.map { tx ->
            TransactionDto(
                id = tx.id,
                amount = tx.amount,
                type = tx.type.name,
                sessionId = tx.sessionId,
                paymentId = tx.paymentId,
                createdAt = tx.createdAt,
                description = tx.description
            )
        })
    }

    @PostMapping("/{id}/balance/adjust")
    @Operation(summary = "Manual balance adjustment by admin")
    fun adjustBalance(
        @PathVariable id: UUID,
        @RequestBody @Valid request: BalanceAdjustRequest
    ): ResponseEntity<BalanceOperationResponse> {
        val adminId = securityUtils.getCurrentUserId()

        val idempotencyKey = "${AdminConstants.IDEMPOTENCY_KEY_ADMIN_PREFIX}${adminId}:${System.currentTimeMillis()}"

        val operationRequest = BalanceOperationRequest(
            type = TransactionType.ADMIN_ADJUSTMENT,
            amount = if (request.amount > BigDecimal.ZERO) request.amount else request.amount.abs(),
            idempotencyKey = idempotencyKey,
            sessionId = null,
            paymentId = null,
            description = "${AdminConstants.TRANSACTION_DESC_ADMIN_PREFIX}${adminId}${AdminConstants.TRANSACTION_DESC_ADMIN_SUFFIX}${request.description}"
        )

        val response = balanceService.applyOperation(id, operationRequest)
        return ResponseEntity.ok(response)
    }

    private fun parseSort(sort: String): Sort {
        val parts = sort.split(",")
        val direction = if (parts.size > 1 && parts[1].uppercase() == AdminConstants.SORT_DIRECTION_DESC) {
            Sort.Direction.DESC
        } else {
            Sort.Direction.ASC
        }
        return Sort.by(direction, parts[0].trim())
    }
}