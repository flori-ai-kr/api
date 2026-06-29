package kr.ai.flori.user.repository

import kr.ai.flori.user.entity.UserConsent
import org.springframework.data.jpa.repository.JpaRepository

/** 가입 동의 기록 저장소. PK가 user_id이므로 findById가 곧 테넌트 격리 조회다. */
interface UserConsentRepository : JpaRepository<UserConsent, Long>
