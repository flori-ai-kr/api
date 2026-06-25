package kr.ai.flori.community.listener

import kr.ai.flori.common.push.PushDispatcher
import kr.ai.flori.common.push.PushTemplates
import kr.ai.flori.common.push.PushTypes
import kr.ai.flori.community.event.CommunityCommentNotifyEvent
import kr.ai.flori.community.event.CommunityNoticePublishedEvent
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 커뮤니티 푸시 발송 리스너. 트랜잭션 커밋 후 비동기로 발송한다(요청 응답을 막지 않음).
 * 공지는 강제(수신설정 무관), 댓글/답글은 수신설정(community_comment) 존중.
 * 비동기 스레드의 예외가 삼켜지지 않도록 최상위에서 포착해 로깅한다(이벤트 소실 가시화).
 */
@Component
class CommunityPushListener(
    private val pushDispatcher: PushDispatcher,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Suppress("TooGenericExceptionCaught") // 비동기 발송 실패가 조용히 사라지지 않도록 일괄 로깅
    fun onNoticePublished(event: CommunityNoticePublishedEvent) {
        try {
            val content = PushTemplates.communityNotice(event.postId, event.title)
            val userIds =
                jdbcTemplate.queryForList(
                    "SELECT id FROM users WHERE is_active AND id <> ?::bigint",
                    Long::class.java,
                    event.authorUserId,
                )
            userIds.forEach { uid ->
                pushDispatcher.sendToUser(uid, content.title, content.body, content.url, PushTypes.COMMUNITY_NOTICE)
            }
        } catch (e: Exception) {
            log.error("공지 푸시 발송 실패 postId={}", event.postId, e)
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Suppress("TooGenericExceptionCaught") // 비동기 발송 실패가 조용히 사라지지 않도록 일괄 로깅
    fun onCommentCreated(event: CommunityCommentNotifyEvent) {
        try {
            val content = PushTemplates.communityComment(event.postId, event.content, event.isReply, event.isSecret)
            pushDispatcher.sendToUser(
                event.recipientUserId,
                content.title,
                content.body,
                content.url,
                PushTypes.COMMUNITY_COMMENT,
            )
        } catch (e: Exception) {
            log.error("댓글 푸시 발송 실패 postId={} recipientId={}", event.postId, event.recipientUserId, e)
        }
    }
}
