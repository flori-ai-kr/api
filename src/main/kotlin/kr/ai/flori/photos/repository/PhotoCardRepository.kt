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
                "ORDER BY pc.updated_at DESC, pc.id DESC LIMIT :limit",
        nativeQuery = true,
    )
    fun findPage(
        @Param("userId") userId: Long,
        @Param("cursorTs") cursorTs: String?,
        @Param("cursorId") cursorId: Long?,
        @Param("tag") tag: String?,
        @Param("customerId") customerId: String?,
        @Param("limit") limit: Int,
    ): List<PhotoCard>

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
}
