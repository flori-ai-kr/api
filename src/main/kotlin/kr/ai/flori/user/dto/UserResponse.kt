package kr.ai.flori.user.dto

data class UserResponse(
    val id: Long,
    val email: String?,
    val nickname: String?,
    /** 가게 프로필. 온보딩 전이면 null. */
    val profile: ProfileResponse?,
)
