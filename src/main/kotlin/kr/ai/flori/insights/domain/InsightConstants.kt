package kr.ai.flori.insights.domain

/**
 * 인사이트 도메인 공통 상수.
 * 스키마 CHECK 제약과 1:1로 맞춘다(DDL: docs/sql/migration/26-06-18-revive-info-feeds.sql).
 */
object TrendCategories {
    const val FLOWER = "flower"
    const val INSPIRATION = "inspiration"
    const val BUSINESS = "business"
    const val INDUSTRY = "industry"

    /** trend_articles.category CHECK 허용값. */
    val ALL = setOf(FLOWER, INSPIRATION, BUSINESS, INDUSTRY)
}

/**
 * 트렌드·뉴스 적재(TrendIngestService)가 순회할 (카테고리, 네이버 검색어) 목록.
 * category 는 TrendCategories(=trend_articles.category CHECK)와 1:1. 검색어는 큐레이션 기본값(튜닝 대상).
 */
object TrendQueries {
    data class Query(
        val category: String,
        val keyword: String,
    )

    val ALL =
        listOf(
            Query(TrendCategories.FLOWER, "화훼"),
            Query(TrendCategories.FLOWER, "꽃 시장"),
            Query(TrendCategories.FLOWER, "절화"),
            Query(TrendCategories.INSPIRATION, "플로리스트"),
            Query(TrendCategories.INSPIRATION, "플라워 디자인"),
            Query(TrendCategories.BUSINESS, "소상공인 지원"),
            Query(TrendCategories.BUSINESS, "자영업 창업"),
            Query(TrendCategories.INDUSTRY, "꽃집"),
            Query(TrendCategories.INDUSTRY, "화훼산업"),
        )
}

object GrantCategories {
    const val FUND = "fund"
    const val MARKETING = "marketing"
    const val EDUCATION = "education"

    /** support_programs.category CHECK 허용값. */
    val ALL = setOf(FUND, MARKETING, EDUCATION)
}

object ScrapTargetTypes {
    const val TREND = "trend"
    const val GRANT = "grant"

    /** insight_scraps.target_type CHECK 허용값. */
    val ALL = setOf(TREND, GRANT)
}

/**
 * 화훼 경매 카테고리(aT f001 flowerGubn). 단일 시장(aT 양재) 응답의 4종 고정.
 * code 는 f001 요청 파라미터(flowerGubn), label 은 응답에 실리는 한글 텍스트(flower_gubn 컬럼).
 */
object FlowerCategories {
    data class Category(
        val code: String,
        val label: String,
    )

    val ALL =
        listOf(
            Category("1", "절화"),
            Category("2", "관엽"),
            Category("3", "난"),
            Category("4", "춘란"),
        )

    /** 적재 cron 이 순회할 flowerGubn 코드. */
    val CODES = ALL.map { it.code }

    /** code → label(응답 텍스트) 매핑. */
    val LABEL_BY_CODE = ALL.associate { it.code to it.label }

    /** 경매 시세 응답 출처 표기(이용허락범위 = "제작자 표시" 준수). */
    const val SOURCE = "화훼유통정보(aT)"
}
