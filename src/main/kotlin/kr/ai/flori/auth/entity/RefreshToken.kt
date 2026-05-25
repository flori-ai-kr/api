package kr.ai.flori.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity
import java.time.Instant
import java.util.UUID

/**
 * refresh 토큰. 원문이 아닌 SHA-256 해시만 저장한다. 회전/로그아웃 시 revoked 처리.
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Column(name = "token_hash", nullable = false, unique = true)
    var tokenHash: String,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    var id: UUID? = null

    @Column(name = "revoked", nullable = false)
    var revoked: Boolean = false
}
