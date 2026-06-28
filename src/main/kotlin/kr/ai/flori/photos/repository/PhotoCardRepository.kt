package kr.ai.flori.photos.repository

import kr.ai.flori.photos.entity.PhotoCard
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PhotoCardRepository : JpaRepository<PhotoCard, Long> {
    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): PhotoCard?

    fun findFirstByUserIdAndSaleId(
        userId: Long,
        saleId: Long,
    ): PhotoCard?

    /** 매출 목록의 썸네일 표시용: 여러 sale_id에 연결된 사진 카드 일괄 조회. */
    fun findByUserIdAndSaleIdIn(
        userId: Long,
        saleIds: Collection<Long>,
    ): List<PhotoCard>

    /**
     * 커서 페이지네이션(updated_at desc) + 선택 필터(tag 포함, 고객 직접 연결 customer_id).
     * NULL 파라미터는 CAST...IS NULL 로 필터 미적용 처리.
     */
    @Query(
        value =
            "SELECT pc.* FROM photo_cards pc " +
                "WHERE pc.user_id = :userId " +
                // (updated_at, id) 복합 키셋 — 동일 updated_at 다수여도 id로 안정적 페이지네이션
                "AND (CAST(:cursorTs AS timestamptz) IS NULL " +
                "  OR pc.updated_at < CAST(:cursorTs AS timestamptz) " +
                "  OR (pc.updated_at = CAST(:cursorTs AS timestamptz) AND pc.id < :cursorId)) " +
                "AND (CAST(:tag AS text) IS NULL OR :tag = ANY(pc.tags)) " +
                "AND (CAST(:customerId AS bigint) IS NULL OR pc.customer_id = CAST(:customerId AS bigint)) " +
                // 기간 필터 — 등록일(created_at) 기준 [from, to). 경계는 호출측이 KST→UTC instant 로 전달.
                "AND (CAST(:from AS timestamptz) IS NULL OR pc.created_at >= CAST(:from AS timestamptz)) " +
                "AND (CAST(:to AS timestamptz) IS NULL OR pc.created_at < CAST(:to AS timestamptz)) " +
                "ORDER BY pc.updated_at DESC, pc.id DESC LIMIT :limit",
        nativeQuery = true,
    )
    @Suppress("LongParameterList")
    fun findPage(
        @Param("userId") userId: Long,
        @Param("cursorTs") cursorTs: String?,
        @Param("cursorId") cursorId: Long?,
        @Param("tag") tag: String?,
        @Param("customerId") customerId: String?,
        @Param("from") from: String?,
        @Param("to") to: String?,
        @Param("limit") limit: Int,
    ): List<PhotoCard>

    /**
     * 상단 요약 헤더용 총계 — findPage 와 동일한 필터(tag·customer_id)를 적용하되 커서 무관 전체 집계.
     * [card_count, photo_count] 2컬럼 단일 행 반환. photo_count 는 jsonb 사진배열 길이 합.
     */
    @Query(
        value =
            "SELECT COUNT(*) AS card_count, " +
                "COALESCE(SUM(jsonb_array_length(pc.photos)), 0) AS photo_count " +
                "FROM photo_cards pc " +
                "WHERE pc.user_id = :userId " +
                "AND (CAST(:tag AS text) IS NULL OR :tag = ANY(pc.tags)) " +
                "AND (CAST(:customerId AS bigint) IS NULL OR pc.customer_id = CAST(:customerId AS bigint)) " +
                "AND (CAST(:from AS timestamptz) IS NULL OR pc.created_at >= CAST(:from AS timestamptz)) " +
                "AND (CAST(:to AS timestamptz) IS NULL OR pc.created_at < CAST(:to AS timestamptz))",
        nativeQuery = true,
    )
    fun countTotals(
        @Param("userId") userId: Long,
        @Param("tag") tag: String?,
        @Param("customerId") customerId: String?,
        @Param("from") from: String?,
        @Param("to") to: String?,
    ): List<Array<Any>>

    /** 태그 삭제 시 해당 태그를 사용하는 카드들에서 태그명 제거. */
    @Modifying
    @Query(
        value = "UPDATE photo_cards SET tags = array_remove(tags, :tagName) WHERE user_id = :userId AND :tagName = ANY(tags)",
        nativeQuery = true,
    )
    fun removeTagFromCards(
        @Param("userId") userId: Long,
        @Param("tagName") tagName: String,
    ): Int

    /** 매출 삭제 시 해당 매출에 연결된 사진 카드의 sale_id를 NULL로(카드 자체는 보존). */
    @Modifying
    @Query("UPDATE PhotoCard p SET p.saleId = null WHERE p.userId = :userId AND p.saleId = :saleId")
    fun clearSaleReference(
        @Param("userId") userId: Long,
        @Param("saleId") saleId: Long,
    ): Int

    /** 고객 삭제 시 해당 고객에 연결된 사진 카드의 customer_id를 NULL로(카드 자체는 보존). */
    @Modifying
    @Query(
        value = "UPDATE photo_cards SET customer_id = NULL WHERE user_id = :userId AND customer_id = :customerId",
        nativeQuery = true,
    )
    fun clearCustomerReference(
        @Param("userId") userId: Long,
        @Param("customerId") customerId: Long,
    ): Int

    /** 정합용: 유저별 갤러리 사진 size 합(jsonb 펼쳐 합산). cross-tenant — @Scheduled 정합에서만 사용. */
    @Query(
        value = """
            SELECT pc.user_id AS userId, COALESCE(SUM((elem->>'size')::bigint), 0) AS bytes
            FROM photo_cards pc, LATERAL jsonb_array_elements(pc.photos) elem
            GROUP BY pc.user_id
        """,
        nativeQuery = true,
    )
    fun sumPhotoBytesByUser(): List<Array<Any>>
}
