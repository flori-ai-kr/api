package kr.ai.flori.auth.dto

import kr.ai.flori.user.dto.ProfileResponse

data class UserResponse(
    val id: Long,
    val email: String?,
    val name: String?,
    /** 가게 프로필. 온보딩 전이면 null. */
    val profile: ProfileResponse?,
)
