package com.hazel.photos.service

import com.hazel.common.error.AppException
import com.hazel.common.error.ErrorCode
import com.hazel.common.tenant.TenantContext
import com.hazel.photos.dto.PhotoTagResponse
import com.hazel.photos.entity.PhotoTag
import com.hazel.photos.repository.PhotoCardRepository
import com.hazel.photos.repository.PhotoTagRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.random.Random

/**
 * 사진 태그 서비스. 태그 삭제 시 카드들에서 해당 태그명을 함께 제거.
 * 모든 쿼리 TenantContext 격리(HARD).
 */
@Service
class PhotoTagService(
    private val photoTagRepository: PhotoTagRepository,
    private val photoCardRepository: PhotoCardRepository,
) {
    @Transactional(readOnly = true)
    fun list(): List<PhotoTagResponse> =
        photoTagRepository
            .findByUserIdOrderByName(TenantContext.currentUserId())
            .map(PhotoTagResponse::from)

    @Transactional
    fun create(
        name: String,
        color: String?,
    ): PhotoTagResponse {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw AppException(ErrorCode.VALIDATION, "태그 이름을 입력해주세요")
        val tag = PhotoTag(TenantContext.currentUserId(), trimmed)
        tag.color = color ?: randomColor()
        return PhotoTagResponse.from(saveUnique(tag))
    }

    @Transactional
    fun update(
        id: UUID,
        name: String,
        color: String,
    ): PhotoTagResponse {
        val tag = load(id)
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw AppException(ErrorCode.VALIDATION, "태그 이름을 입력해주세요")
        tag.name = trimmed
        tag.color = color
        return PhotoTagResponse.from(saveUnique(tag))
    }

    @Transactional
    fun delete(id: UUID) {
        val tag = load(id)
        photoCardRepository.removeTagFromCards(tag.userId, tag.name)
        photoTagRepository.delete(tag)
    }

    private fun saveUnique(tag: PhotoTag): PhotoTag =
        try {
            // saveAndFlush: unique 위반을 같은 트랜잭션 내에서 즉시 표면화해 포착
            photoTagRepository.saveAndFlush(tag)
        } catch (_: DataIntegrityViolationException) {
            throw AppException(ErrorCode.DUPLICATE, "이미 존재하는 태그입니다")
        }

    private fun load(id: UUID): PhotoTag =
        photoTagRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(ErrorCode.NOT_FOUND, "태그를 찾을 수 없습니다")

    private fun randomColor(): String = TAG_COLORS[Random.nextInt(TAG_COLORS.size)]

    private companion object {
        val TAG_COLORS =
            listOf(
                "#f5f5f5",
                "#ec4899",
                "#ef4444",
                "#eab308",
                "#a855f7",
                "#6366f1",
                "#14b8a6",
                "#f97316",
                "#22c55e",
                "#3b82f6",
            )
    }
}
