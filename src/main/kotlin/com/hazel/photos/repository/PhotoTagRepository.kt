package com.hazel.photos.repository

import com.hazel.photos.entity.PhotoTag
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PhotoTagRepository : JpaRepository<PhotoTag, UUID> {
    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): PhotoTag?

    fun findByUserIdOrderByName(userId: UUID): List<PhotoTag>
}
