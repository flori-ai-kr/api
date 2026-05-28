package kr.ai.flori.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity

/**
 * 소셜 전용 인증 사용자(V1 users 테이블 매핑).
 *
 * - 소셜 로그인(KAKAO/GOOGLE/NAVER)으로만 생성된다. 이메일/비밀번호 가입은 없다(비밀번호 컬럼 제거).
 * - User 행은 온보딩 완료(register/complete) 시점에만 생성된다 — 온보딩 중도 이탈 시 유령 계정이 남지 않는다.
 * - email은 온보딩에서 항상 채워지며(NOT NULL) 이후 수정 가능하다. name은 계정 표시명(닉네임).
 * - 신원은 (provider, providerId)로 식별한다.
 */
@Entity
@Table(name = "users")
class User(
    @Column(name = "email", nullable = false, unique = true)
    var email: String,
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
