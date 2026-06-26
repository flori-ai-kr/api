package kr.ai.flori.verification.repository

import kr.ai.flori.verification.domain.BusinessVerificationStatuses
import kr.ai.flori.verification.entity.BusinessVerification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface BusinessVerificationRepository : JpaRepository<BusinessVerification, Long> {
    /** 게이팅/중복검사용: 특정 상태의 신청 존재 여부(멀티테넌시: 항상 userId로 격리). */
    fun existsByUserIdAndStatus(
        userId: Long,
        status: BusinessVerificationStatuses,
    ): Boolean

    /** 상태 조회용: 사용자의 최신 신청 1건. */
    fun findFirstByUserIdOrderByCreatedAtDesc(userId: Long): BusinessVerification?

    /** 빌링 체험 자격 키 산출용: 사용자의 특정 상태(APPROVED) 최신 신청 1건. */
    fun findFirstByUserIdAndStatusOrderByCreatedAtDesc(
        userId: Long,
        status: BusinessVerificationStatuses,
    ): BusinessVerification?

    /** 운영 콘솔: 상태별 신청 목록(최신순). cross-tenant — @RequiresAdmin 하위에서만 사용. */
    fun findByStatusOrderByCreatedAtDesc(
        status: BusinessVerificationStatuses,
        pageable: Pageable,
    ): Page<BusinessVerification>
}
