package kr.ai.flori.verification.repository

import kr.ai.flori.verification.domain.BusinessVerificationStatuses
import kr.ai.flori.verification.entity.BusinessVerification
import org.springframework.data.jpa.repository.JpaRepository

interface BusinessVerificationRepository : JpaRepository<BusinessVerification, Long> {
    /** 게이팅/중복검사용: 특정 상태의 신청 존재 여부(멀티테넌시: 항상 userId로 격리). */
    fun existsByUserIdAndStatus(
        userId: Long,
        status: BusinessVerificationStatuses,
    ): Boolean

    /** 상태 조회용: 사용자의 최신 신청 1건. */
    fun findFirstByUserIdOrderByCreatedAtDesc(userId: Long): BusinessVerification?
}
