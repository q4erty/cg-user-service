package com.cloudgaming.userservice.persistence

import com.cloudgaming.userservice.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, UUID> {

    fun findByKeycloakId(keycloakId: String): User?

    fun existsByKeycloakId(keycloakId: String): Boolean

    fun findByEmailContainingIgnoreCase(email: String, pageable: org.springframework.data.domain.Pageable):
            org.springframework.data.domain.Page<User>

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.lastLoginAt = :timestamp WHERE u.keycloakId = :keycloakId")
    fun updateLastLoginAt(
        @Param("keycloakId") keycloakId: String,
        @Param("timestamp") timestamp: Instant
    ): Int
}