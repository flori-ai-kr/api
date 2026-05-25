package kr.ai.flori.auth.repository

import kr.ai.flori.auth.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshToken?
}
