package kr.ai.flori.auth.dto

import kr.ai.flori.user.dto.ProfileResponse

data class UserResponse(
    val id: Long,
    val email: String?,
    val name: String?,
    /** 온보딩 완료 여부. 웹은 false일 때만 /onboarding으로 라우팅한다. */
    val onboarded: Boolean,
    /** 가게 프로필. 온보딩 전이면 null. */
    val profile: ProfileResponse?,
)
