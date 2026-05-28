package kr.ai.flori.common.security

/**
 * 인증된 요청의 주체. SecurityContext와 컨트롤러에서 현재 사용자 식별에 사용.
 */
data class UserPrincipal(
    val userId: Long,
    val email: String,
)
