package com.cloudgaming.userservice.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(name = "users")
class User(

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "keycloak_id", nullable = false, unique = true, updatable = false)
    val keycloakId: String,

    @Column(name = "email", nullable = false)
    var email: String,

    @Column(name = "display_name")
    var displayName: String? = null,

    @Column(name = "avatar_url", length = 1024)
    var avatarUrl: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "User(id=$id, keycloakId='$keycloakId', email='$email', displayName=$displayName)"
}