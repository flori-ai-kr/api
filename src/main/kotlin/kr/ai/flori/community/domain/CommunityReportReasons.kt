package kr.ai.flori.community.domain

/**
 * 커뮤니티 신고 사유(SSOT). DB CHECK 제약·앱 계약과 일치한다.
 */
object CommunityReportReasons {
    const val SPAM = "spam"
    const val ABUSE = "abuse"
    const val PRIVACY = "privacy"
    const val SEXUAL = "sexual"
    const val ETC = "etc"

    val ALL = setOf(SPAM, ABUSE, PRIVACY, SEXUAL, ETC)
}
