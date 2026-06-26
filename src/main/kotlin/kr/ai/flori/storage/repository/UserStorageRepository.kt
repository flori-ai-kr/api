package kr.ai.flori.storage.repository

import kr.ai.flori.storage.entity.UserStorage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface UserStorageRepository : JpaRepository<UserStorage, Long> {
    fun findByUserId(userId: Long): UserStorage?

    /** 사용량 원자 증감(0 클램프 + updated_at 갱신). delta 음수=감분. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
        value =
            "UPDATE user_storage SET used_bytes = GREATEST(used_bytes + :delta, 0), updated_at = NOW() " +
                "WHERE user_id = :userId",
        nativeQuery = true,
    )
    fun addUsedBytes(
        @Param("userId") userId: Long,
        @Param("delta") delta: Long,
    ): Int

    /** 정합 작업용: 사용량을 절대값으로 덮어쓴다. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
        value = "UPDATE user_storage SET used_bytes = GREATEST(:bytes, 0), updated_at = NOW() WHERE user_id = :userId",
        nativeQuery = true,
    )
    fun setUsedBytes(
        @Param("userId") userId: Long,
        @Param("bytes") bytes: Long,
    ): Int
}
