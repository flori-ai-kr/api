package kr.ai.flori.common.validation

/**
 * 자유입력(free-text) 필드의 길이 상한. 컨트롤러 경계(`@field:Size`)에서 400(E-CMN-001)으로 차단해
 * DB 제약·오버플로로 새지 않게 한다(TEXT 컬럼은 DB 무제한이므로 애플리케이션이 유일한 방어선).
 *
 * 값 기준: VARCHAR(n) 컬럼은 n에 맞추고, TEXT 컬럼은 합리적 애플리케이션 상한으로 정한다.
 */
object FieldLimits {
    const val NAME = 100 // users.nickname·customers.name VARCHAR(100)
    const val STORE_NAME = 100 // user_profiles.store_name TEXT
    const val EMAIL = 255 // users.email VARCHAR(255)
    const val PHONE = 20 // *.customer_phone·customers.phone VARCHAR(20)
    const val REGION = 50
    const val AGE_RANGE = 20
    const val LABEL = 100 // *_settings.label VARCHAR(100)
    const val VALUE = 100 // *_settings.value VARCHAR(100)
    const val COLOR = 7 // *_settings.color VARCHAR(7) (#rrggbb)
    const val PRODUCT_CATEGORY = 100 // sales.product_category VARCHAR(100)
    const val ITEM_NAME = 200 // expenses.item_name TEXT
    const val VENDOR = 100 // card_companies.vendor VARCHAR(100), expenses.vendor TEXT
    const val CARD_COMPANY = 50 // expenses.card_company VARCHAR(50)
    const val TITLE = 200 // community/reservations title TEXT
    const val NOTE = 1000 // *.note TEXT (자유 메모)
    const val DESCRIPTION = 2000 // reservations.description TEXT
    const val MEMO = 500 // insight_scraps.memo TEXT
    const val COMMENT = 2000 // community_comments.content TEXT
    const val CONTENT_TEXT = 20_000 // community_posts.content_text (본문 플레인텍스트)
    const val CONTENT_JSON = 100_000 // community_posts.content(jsonb) 직렬화 상한(서비스 검사)
}
