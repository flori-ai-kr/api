package kr.ai.flori.user.repository

import kr.ai.flori.user.entity.UserProfile
import org.springframework.data.jpa.repository.JpaRepository

/** 사용자 프로필 저장소. PK가 user_id이므로 findById가 곧 테넌트 격리 조회다. */
interface UserProfileRepository : JpaRepository<UserProfile, Long>
