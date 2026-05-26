package kr.ai.flori.auth.dto

import java.util.UUID

data class UserResponse(
    val id: UUID,
    val email: String?,
    val name: String?,
)
