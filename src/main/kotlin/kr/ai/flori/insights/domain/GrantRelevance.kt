package kr.ai.flori.insights.domain

/**
 * 지원사업 소상공인(꽃집) 관련도 필터.
 *
 * K-Startup·기업마당 두 소스 모두 전국·전분야 공고(스타트업·제조·R&D·지자체 타업종 등)를 통째로 준다.
 * 꽃집 사장님(소상공인)이 실제 신청 가능한 공고만 남기려고, 제목·요약·지원대상 중 하나라도
 * 아래 키워드를 포함하는 공고만 적재한다(부분일치). 화훼/꽃 전용 공고가 드물게 있으면 함께 포착한다.
 *
 * 키워드는 큐레이션 기본값(튜닝 대상) — 너무 좁히면 코퍼스가 비고, 너무 넓히면 노이즈가 섞인다.
 */
object GrantRelevance {
    val KEYWORDS =
        listOf(
            // 소상공인 일반
            "소상공인",
            "소공인",
            "자영업",
            "골목상권",
            "전통시장",
            "상점가",
            "백년가게",
            "점포",
            "생활밀착",
            "1인기업",
            "1인 기업",
            // 화훼·꽃 전용(드물지만 있으면 반드시 포착)
            "화훼",
            "원예",
            "플로리스트",
            "꽃집",
            "화원",
            "꽃 도매",
            "꽃도매",
        )

    /** 제목·요약·지원대상 등 텍스트 중 하나라도 관련 키워드를 포함하면 true. */
    fun isRelevant(vararg texts: String?): Boolean {
        val haystack = texts.filterNotNull().joinToString(" ")
        if (haystack.isBlank()) return false
        return KEYWORDS.any { haystack.contains(it) }
    }
}
