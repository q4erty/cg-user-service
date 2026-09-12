package com.cloudgaming.userservice.dto

import java.math.RoundingMode
import java.security.MessageDigest
import java.util.*

internal data class CachedIdempotentResult(
    val fingerprint: String,
    val response: BalanceOperationResponse
) {

    fun matches(userId: UUID, request: BalanceOperationRequest): Boolean =
        fingerprint == fingerprintOf(userId, request)

    companion object {

        fun fingerprintOf(userId: UUID, request: BalanceOperationRequest): String {
            val canonical = listOf(
                userId.toString(),
                request.type.name,
                request.amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                request.sessionId?.toString().orEmpty(),
                request.paymentId?.toString().orEmpty(),
                request.description.orEmpty()
            ).joinToString("|")
            val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            return digest.joinToString(separator = "") { "%02x".format(it) }
        }
    }
}
