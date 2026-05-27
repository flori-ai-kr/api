package kr.ai.flori.auth.repository

import kr.ai.flori.auth.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean

    fun findByProviderAndProviderId(
        provider: String,
        providerId: String,
    ): User?
}
