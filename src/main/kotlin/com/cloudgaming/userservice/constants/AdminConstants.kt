package com.cloudgaming.userservice.constants

object AdminConstants {
    const val DEFAULT_CURRENCY = "KZT"
    const val DEFAULT_SORT = "createdAt,desc"

    const val ACTION_ADD = "ADD"
    const val ACTION_REMOVE = "REMOVE"

    const val IDEMPOTENCY_KEY_ADMIN_PREFIX = "admin:"
    const val TRANSACTION_DESC_ADMIN_PREFIX = "[Admin "
    const val TRANSACTION_DESC_ADMIN_SUFFIX = "] "
    const val SORT_DIRECTION_DESC = "DESC"

    const val ERROR_KEYCLOAK_UNAVAILABLE = "Keycloak is currently unavailable, please retry later"
}