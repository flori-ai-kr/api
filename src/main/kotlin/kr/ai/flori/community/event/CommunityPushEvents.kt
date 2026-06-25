package kr.ai.flori.community.event

/** 커뮤니티 공지글 발행 → 전체 활성 유저(작성자 제외)에게 푸시. */
data class CommunityNoticePublishedEvent(
    val postId: Long,
    val title: String,
    val authorUserId: Long,
)

/**
 * 댓글/답글 발생 → 수신자 1인에게 푸시. 수신자는 글쓴이(댓글) 또는 부모댓글 작성자(답글).
 * 비밀이면 본문을 마스킹한다(템플릿에서 처리).
 */
data class CommunityCommentNotifyEvent(
    val postId: Long,
    val recipientUserId: Long,
    val content: String,
    val isReply: Boolean,
    val isSecret: Boolean,
)
