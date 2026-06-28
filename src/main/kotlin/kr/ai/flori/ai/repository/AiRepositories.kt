package kr.ai.flori.ai.repository

import kr.ai.flori.ai.entity.AiChatMessage
import kr.ai.flori.ai.entity.AiChatSession
import kr.ai.flori.ai.entity.AiMarketingContent
import kr.ai.flori.ai.entity.AiProactiveLog
import kr.ai.flori.ai.entity.AiPrompt
import kr.ai.flori.ai.entity.AiToneProfile
import kr.ai.flori.ai.entity.AiWriteProposal
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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

/** 말투 프로필은 유저당 1행 — user_id로 조회/upsert(테넌트 격리). */
interface AiToneProfileRepository : JpaRepository<AiToneProfile, Long> {
    fun findByUserId(userId: Long): AiToneProfile?
}

interface AiMarketingContentRepository : JpaRepository<AiMarketingContent, Long> {
    /** 목록: 소프트삭제 제외 + user_id·channel 격리, 최신순(Pageable.sort 적용). */
    fun findByUserIdAndChannelAndDeletedAtIsNull(
        userId: Long,
        channel: String,
        pageable: Pageable,
    ): Page<AiMarketingContent>

    /** 상세/삭제: 소프트삭제 제외 + user_id 격리. */
    fun findByIdAndUserIdAndDeletedAtIsNull(
        id: Long,
        userId: Long,
    ): AiMarketingContent?
}

/**
 * AI 프롬프트 레지스트리(SPEC-AI-008). user 데이터가 아니라 운영 자산이므로 user_id 격리 없음 —
 * 접근은 콘솔(@RequiresAdmin)과 게이트웨이 내부 로드로만 제한된다.
 */
interface AiPromptRepository : JpaRepository<AiPrompt, Long> {
    /** 생성 시 주입할 active 프롬프트(채널당 1개). 없으면 null=폴백 신호. */
    fun findFirstByChannelAndIsActiveTrueAndDeletedAtIsNull(channel: String): AiPrompt?

    /** 콘솔 목록: 채널별, active 먼저 + 최신순(소프트삭제 제외). */
    fun findByChannelAndDeletedAtIsNullOrderByIsActiveDescCreatedAtDesc(channel: String): List<AiPrompt>

    /** 콘솔 상세/수정/삭제: 소프트삭제 제외. */
    fun findByIdAndDeletedAtIsNull(id: Long): AiPrompt?

    /** (channel, version) 중복 방지 검증용. */
    fun findByChannelAndVersionAndDeletedAtIsNull(
        channel: String,
        version: String,
    ): AiPrompt?
}
