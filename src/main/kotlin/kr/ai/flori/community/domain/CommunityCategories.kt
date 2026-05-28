package kr.ai.flori.community.domain

/**
 * 커뮤니티 게시판 카테고리(SSOT). DB CHECK 제약·앱 계약과 일치한다.
 * notice(공지)는 관리자만 작성 가능(서비스에서 강제), 그 외는 전체 작성 가능.
 */
object CommunityCategories {
    const val NOTICE = "notice"
    const val DAILY = "daily"
    const val QUESTION = "question"
    const val KNOWLEDGE = "knowledge"
    const val REVIEW = "review"
    const val ETC = "etc"

    val ALL = setOf(NOTICE, DAILY, QUESTION, KNOWLEDGE, REVIEW, ETC)

    /** 관리자만 작성 가능한 카테고리. */
    val ADMIN_ONLY = setOf(NOTICE)
}
