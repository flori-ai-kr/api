package kr.ai.flori.customers.repository

import kr.ai.flori.customers.dto.CustomerStats
import kr.ai.flori.customers.dto.PhotoThumbnail
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * 고객 도메인의 네이티브 SQL 집계 전용 리포지토리.
 * 구매 통계는 sales 실시간 집계(SSOT), 대표 썸네일은 photo_cards jsonb 첫 요소.
 * 모든 메서드는 호출부가 넘긴 userId로 격리한다(멀티테넌시 HARD).
 */
@Repository
class CustomerQueryRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    /** 단일 고객 구매 통계(건수·총액·최초·최근 구매일). */
    fun statsFor(
        userId: Long,
        customerId: Long,
    ): CustomerStats =
        jdbcTemplate
            .query(
                "SELECT count(*) AS cnt, COALESCE(SUM(amount), 0) AS total, MIN(date) AS first_date, MAX(date) AS last_date " +
                    "FROM sales WHERE user_id = ?::bigint AND customer_id = ?::bigint",
                { rs, _ ->
                    CustomerStats(
                        rs.getInt("cnt"),
                        rs.getLong("total"),
                        rs.getDate("first_date")?.toLocalDate(),
                        rs.getDate("last_date")?.toLocalDate(),
                    )
                },
                userId,
                customerId,
            ).firstOrNull() ?: CustomerStats.EMPTY

    /** 테넌트 전체의 고객별 구매 통계 맵(1쿼리). */
    fun aggregateStats(userId: Long): Map<Long, CustomerStats> =
        jdbcTemplate
            .query(
                "SELECT customer_id, count(*) AS cnt, COALESCE(SUM(amount), 0) AS total, " +
                    "MIN(date) AS first_date, MAX(date) AS last_date " +
                    "FROM sales WHERE user_id = ?::bigint AND customer_id IS NOT NULL GROUP BY customer_id",
                { rs, _ ->
                    rs.getLong("customer_id") to
                        CustomerStats(
                            rs.getInt("cnt"),
                            rs.getLong("total"),
                            rs.getDate("first_date")?.toLocalDate(),
                            rs.getDate("last_date")?.toLocalDate(),
                        )
                },
                userId,
            ).toMap()

    /** 고객별 구매(매출) 건수 일괄 조회. 예약 카드의 'N번 방문' 배지 등 enrichment 용도. */
    fun purchaseCounts(userId: Long): Map<Long, Int> =
        jdbcTemplate
            .query(
                "SELECT customer_id, count(*) AS cnt FROM sales " +
                    "WHERE user_id = ?::bigint AND customer_id IS NOT NULL GROUP BY customer_id",
                { rs, _ -> rs.getLong("customer_id") to rs.getInt("cnt") },
                userId,
            ).toMap()

    /**
     * 고객별 대표 썸네일 6장 + 카운트 (1쿼리). photos jsonb 의 첫 요소 url.
     * photos jsonb 형태: [{"url":...,"originalName":...}] — photos->0->>'url' 이 대표 썸네일.
     * thumb_urls / thumb_ids 는 동일한 ORDER BY + FILTER 적용으로 인덱스 정렬이 보장된다.
     */
    fun photoSummaryByCustomer(userId: Long): Map<Long, Pair<List<PhotoThumbnail>, Int>> =
        jdbcTemplate
            .query(
                """
                SELECT customer_id,
                       count(*) AS cnt,
                       (array_agg((photos->0->>'url') ORDER BY created_at DESC)
                          FILTER (WHERE jsonb_array_length(photos) > 0))[1:6] AS thumb_urls,
                       (array_agg(id            ORDER BY created_at DESC)
                          FILTER (WHERE jsonb_array_length(photos) > 0))[1:6] AS thumb_ids
                FROM photo_cards
                WHERE user_id = ?::bigint AND customer_id IS NOT NULL
                GROUP BY customer_id
                """.trimIndent(),
                { rs, _ ->
                    val thumbs =
                        mapThumbnails(
                            rs.getArray("thumb_urls")?.array as? Array<*>,
                            rs.getArray("thumb_ids")?.array as? Array<*>,
                        )
                    rs.getLong("customer_id") to (thumbs to rs.getInt("cnt"))
                },
                userId,
            ).toMap()

    /** 단일 고객 대표 썸네일 6장 + 카운트. thumb_urls / thumb_ids 는 동일 ORDER BY + FILTER 로 정렬이 보장된다. */
    fun photoSummaryFor(
        userId: Long,
        customerId: Long,
    ): Pair<List<PhotoThumbnail>, Int> =
        jdbcTemplate
            .query(
                """
                SELECT count(*) AS cnt,
                       (array_agg((photos->0->>'url') ORDER BY created_at DESC)
                          FILTER (WHERE jsonb_array_length(photos) > 0))[1:6] AS thumb_urls,
                       (array_agg(id            ORDER BY created_at DESC)
                          FILTER (WHERE jsonb_array_length(photos) > 0))[1:6] AS thumb_ids
                FROM photo_cards
                WHERE user_id = ?::bigint AND customer_id = ?::bigint
                """.trimIndent(),
                { rs, _ ->
                    val thumbs =
                        mapThumbnails(
                            rs.getArray("thumb_urls")?.array as? Array<*>,
                            rs.getArray("thumb_ids")?.array as? Array<*>,
                        )
                    thumbs to rs.getInt("cnt")
                },
                userId,
                customerId,
            ).firstOrNull() ?: (emptyList<PhotoThumbnail>() to 0)

    /** url·id 배열을 같은 인덱스로 짝지어 썸네일 목록으로 변환(두 배열은 동일 정렬 보장). */
    private fun mapThumbnails(
        urlArr: Array<*>?,
        idArr: Array<*>?,
    ): List<PhotoThumbnail> =
        urlArr
            ?.indices
            ?.mapNotNull { i ->
                val url = urlArr[i] as? String ?: return@mapNotNull null
                val cardId = (idArr?.getOrNull(i) as? Long) ?: return@mapNotNull null
                PhotoThumbnail(url, cardId)
            } ?: emptyList()
}
