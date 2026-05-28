package kr.ai.flori.user.repository

import kr.ai.flori.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean

    /** 닉네임 중복 검사 — 가입 시 사용. 정확 일치(case-sensitive). */
    fun existsByNickname(nickname: String): Boolean

    /** 닉네임 중복 검사(본인 제외) — 프로필 편집 시 본인 닉네임 유지를 오탐하지 않도록 자기 행은 제외. */
    fun existsByNicknameAndIdNot(
        nickname: String,
        id: Long,
    ): Boolean

    fun findByProviderAndProviderId(
        provider: String,
        providerId: String,
    ): User?
}
