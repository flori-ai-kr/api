package kr.ai.flori.ai.repository

import kr.ai.flori.ai.entity.AiChatMessage
import kr.ai.flori.ai.entity.AiChatSession
import kr.ai.flori.ai.entity.AiProactiveLog
import kr.ai.flori.ai.entity.AiWriteProposal
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/** 모든 조회는 user_id 격리(HARD). session_token은 전역 UNIQUE라 소유자까지 함께 검증한다. */
interface AiChatSessionRepository : JpaRepository<AiChatSession, Long> {
    fun findByUserIdAndSessionTokenAndDeletedAtIsNull(
        userId: Long,
        sessionToken: String,
    ): AiChatSession?
}

interface AiChatMessageRepository : JpaRepository<AiChatMessage, Long> {
    /** 멀티턴 컨텍스트 로드(시간순). 세션 소유는 사전 검증하지만 user_id로 방어적 격리도 적용. */
    fun findBySessionIdAndUserIdOrderByCreatedAtAsc(
        sessionId: Long,
        userId: Long,
    ): List<AiChatMessage>

    /** 일일 사용량 캡 집계용(유저별 기준시각 이후 메시지 수). */
    fun countByUserIdAndCreatedAtAfter(
        userId: Long,
        after: Instant,
    ): Long
}

interface AiWriteProposalRepository : JpaRepository<AiWriteProposal, Long> {
    fun findByProposalIdAndUserId(
        proposalId: String,
        userId: Long,
    ): AiWriteProposal?
}

interface AiProactiveLogRepository : JpaRepository<AiProactiveLog, Long>
