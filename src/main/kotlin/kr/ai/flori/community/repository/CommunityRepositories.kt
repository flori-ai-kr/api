package kr.ai.flori.community.repository

import kr.ai.flori.community.entity.CommunityComment
import kr.ai.flori.community.entity.CommunityLike
import kr.ai.flori.community.entity.CommunityPost
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CommunityPostRepository : JpaRepository<CommunityPost, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): CommunityPost?

    /** 목록: 미삭제만, 고정글 우선 + 최신순. 카테고리/검색(content_text·title) 필터. */
    @Query(
        "SELECT p FROM CommunityPost p " +
            "WHERE p.deletedAt IS NULL " +
            "AND (:category IS NULL OR p.category = :category) " +
            "AND (:search IS NULL OR LOWER(p.title) LIKE :search OR LOWER(p.contentText) LIKE :search) " +
            "ORDER BY p.isPinned DESC, p.createdAt DESC",
    )
    fun findFeed(
        @Param("category") category: String?,
        @Param("search") search: String?,
        pageable: Pageable,
    ): Page<CommunityPost>

    // flushAutomatically: 호출 전 보류 중인 변경(예: 댓글 soft delete)을 먼저 flush — 안 그러면 clear가 유실시킴.
    // clearAutomatically: 벌크 UPDATE 후 영속성 컨텍스트를 비워 재조회가 최신 카운트를 읽게 한다.
    // 조건의 (likeCount + :delta) >= 0 으로 동시성 시에도 음수 카운트를 방지한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CommunityPost p SET p.likeCount = p.likeCount + :delta WHERE p.id = :id AND p.likeCount + :delta >= 0")
    fun adjustLikeCount(
        @Param("id") id: Long,
        @Param("delta") delta: Int,
    )

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CommunityPost p SET p.commentCount = p.commentCount + :delta WHERE p.id = :id AND p.commentCount + :delta >= 0")
    fun adjustCommentCount(
        @Param("id") id: Long,
        @Param("delta") delta: Int,
    )
}

interface CommunityCommentRepository : JpaRepository<CommunityComment, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): CommunityComment?

    /** 글의 댓글 전체(삭제 포함 — 톰스톤으로 스레드 유지). 작성순. */
    fun findByPostIdOrderByCreatedAtAsc(postId: Long): List<CommunityComment>

    /**
     * 댓글의 조상 깊이(자신=1, 부모마다 +1)를 단일 재귀 CTE로 계산. 대댓글 깊이 검증에서 단건 반복조회(N+1)를 대체.
     * 삭제된 조상도 깊이에 포함(스레드 구조 보존). 인자 댓글이 없으면 0.
     */
    @Query(
        value =
            "WITH RECURSIVE chain AS (" +
                "SELECT id, parent_id, 1 AS depth FROM community_comments WHERE id = :commentId " +
                "UNION ALL " +
                "SELECT c.id, c.parent_id, chain.depth + 1 FROM community_comments c " +
                "JOIN chain ON c.id = chain.parent_id) " +
                "SELECT COALESCE(MAX(depth), 0) FROM chain",
        nativeQuery = true,
    )
    fun ancestorDepth(
        @Param("commentId") commentId: Long,
    ): Int
}

interface CommunityLikeRepository : JpaRepository<CommunityLike, Long> {
    fun findByPostIdAndUserId(
        postId: Long,
        userId: Long,
    ): CommunityLike?

    fun existsByPostIdAndUserId(
        postId: Long,
        userId: Long,
    ): Boolean

    /** 현재 사용자가 좋아요한 글 id 목록(목록 응답의 liked 플래그용). */
    @Query("SELECT l.postId FROM CommunityLike l WHERE l.userId = :userId AND l.postId IN :postIds")
    fun findLikedPostIds(
        @Param("userId") userId: Long,
        @Param("postIds") postIds: Collection<Long>,
    ): List<Long>
}
