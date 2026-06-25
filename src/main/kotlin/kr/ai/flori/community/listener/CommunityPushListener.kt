package kr.ai.flori.community.listener

import kr.ai.flori.common.push.PushDispatcher
import kr.ai.flori.common.push.PushTemplates
import kr.ai.flori.common.push.PushTypes
import kr.ai.flori.community.event.CommunityCommentNotifyEvent
import kr.ai.flori.community.event.CommunityNoticePublishedEvent
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 커뮤니티 푸시 발송 리스너. 트랜잭션 커밋 후 비동기로 발송한다(요청 응답을 막지 않음).
 * 공지는 강제(수신설정 무관), 댓글/답글은 수신설정(community_comment) 존중.
 */
@Component
class CommunityPushListener(
    private val pushDispatcher: PushDispatcher,
    private val jdbcTemplate: JdbcTemplate,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onNoticePublished(event: CommunityNoticePublishedEvent) {
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
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onCommentCreated(event: CommunityCommentNotifyEvent) {
        val content = PushTemplates.communityComment(event.postId, event.content, event.isReply, event.isSecret)
        pushDispatcher.sendToUser(
            event.recipientUserId,
            content.title,
            content.body,
            content.url,
            PushTypes.COMMUNITY_COMMENT,
        )
    }
}
