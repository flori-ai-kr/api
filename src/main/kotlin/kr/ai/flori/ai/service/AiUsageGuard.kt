package kr.ai.flori.ai.service

import kr.ai.flori.ai.config.AiGatewayProperties
import kr.ai.flori.ai.entity.AiChatMessage
import kr.ai.flori.ai.repository.AiChatMessageRepository
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

/**
 * AI 일일 사용량 캡의 원자적 강제. [AiService]와 별도 빈으로 두어 `@Transactional` 프록시가 적용되게 한다
 * (같은 빈 내부 호출이면 트랜잭션이 안 걸림).
 *
 * count→check→insert 사이의 TOCTOU(동시요청이 모두 캡 미만을 보고 통과)를 막기 위해,
 * 트랜잭션 범위의 per-user advisory lock([pg_advisory_xact_lock])으로 같은 사용자의 요청을 직렬화한다.
 * 카운트 기준이 되는 유저 메시지 INSERT를 같은 락 안에서 수행해야 다음 요청의 카운트에 즉시 반영된다.
 */
@Component
class AiUsageGuard(
    private val jdbcTemplate: JdbcTemplate,
    private val messageRepository: AiChatMessageRepository,
    private val properties: AiGatewayProperties,
) {
    /**
     * 일일 캡을 원자적으로 강제하고 유저 메시지를 기록한다. 캡 초과면 [AppException](FORBIDDEN)을 던져
     * 트랜잭션을 롤백(메시지 미기록)한다. 정상이면 저장된 유저 메시지를 반환한다.
     */
    @Transactional
    fun admitChatMessage(
        userId: Long,
        sessionId: Long,
        content: String,
    ): AiChatMessage {
        // 같은 사용자의 동시요청을 직렬화(트랜잭션 종료 시 자동 해제).
        jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(?)", { _, _ -> 0 }, userId)
        if (isOverCap(userId)) {
            throw AppException(CommonErrorCode.FORBIDDEN, CAP_MESSAGE)
        }
        return messageRepository.save(AiChatMessage(sessionId, userId, ROLE_USER, content))
    }

    /** 비원자적 사전 차단용(세션 생성·외부호출 전 빠른 거부). 최종 보장은 [admitChatMessage]가 한다. */
    @Transactional(readOnly = true)
    fun isOverCap(userId: Long): Boolean {
        val startOfDay = LocalDate.now(SEOUL).atStartOfDay(SEOUL).toInstant()
        return messageRepository.countByUserIdAndCreatedAtAfter(userId, startOfDay) >= properties.usageCapPerDay
    }

    private companion object {
        const val ROLE_USER = "user"
        const val CAP_MESSAGE = "오늘 AI 사용량을 모두 사용했어요. 내일 다시 이용해 주세요."
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
