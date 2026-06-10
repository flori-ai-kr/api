package kr.ai.flori.photos.repository

import kr.ai.flori.photos.entity.PhotoTag
import org.springframework.data.jpa.repository.JpaRepository

interface PhotoTagRepository : JpaRepository<PhotoTag, Long> {
    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): PhotoTag?

    fun findByUserIdOrderByName(userId: Long): List<PhotoTag>
}
