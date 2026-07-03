package com.cloudgaming.userservice.container

import dasniko.testcontainers.keycloak.KeycloakContainer

object KeycloakTestContainerSingleton {
    const val ADMIN_USERNAME = "admin"
    const val ADMIN_PASSWORD = "admin"
    const val MASTER_REALM = "master"
    const val TARGET_REALM = "cloud-gaming"

    val instance: KeycloakContainer = KeycloakContainer("quay.io/keycloak/keycloak:24.0")
        .withAdminUsername(ADMIN_USERNAME)
        .withAdminPassword(ADMIN_PASSWORD)
        .also { it.start() }
}
