package kr.ai.flori.auth.repository

import kr.ai.flori.auth.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean

    fun findByProviderAndProviderId(
        provider: String,
        providerId: String,
    ): User?
}
