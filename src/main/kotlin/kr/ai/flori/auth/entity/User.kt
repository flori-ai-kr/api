package kr.ai.flori.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import java.util.UUID

/**
 * 자체 인증 사용자. (원본 Supabase auth 테이블 대체 — V1 users 테이블에 매핑)
 */
@Entity
@Table(name = "users")
class User(
    @Column(name = "email", nullable = false, unique = true)
    var email: String,
    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,
    @Column(name = "name")
    var name: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    var id: UUID? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
}
