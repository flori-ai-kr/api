package kr.ai.flori.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity

/**
 * 자체 인증 사용자. (원본 Supabase auth 테이블 대체 — V1 users 테이블에 매핑)
 * 소셜 사용자는 email/passwordHash가 null이고 provider/providerId로 식별한다.
 */
@Entity
@Table(name = "users")
class User(
    @Column(name = "email", unique = true)
    var email: String? = null,
    @Column(name = "password_hash")
    var passwordHash: String? = null,
    @Column(name = "name")
    var name: String? = null,
    @Column(name = "provider", nullable = false)
    var provider: String = "LOCAL",
    @Column(name = "provider_id")
    var providerId: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
}
