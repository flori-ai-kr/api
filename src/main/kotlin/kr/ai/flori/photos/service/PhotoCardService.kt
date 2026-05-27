package kr.ai.flori.photos.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import kr.ai.flori.common.storage.S3PresignService
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.photos.dto.FileMetaRequest
import kr.ai.flori.photos.dto.PhotoCardCreateRequest
import kr.ai.flori.photos.dto.PhotoCardResponse
import kr.ai.flori.photos.dto.PhotoCardUpdateRequest
import kr.ai.flori.photos.dto.PhotoCardsPageResponse
import kr.ai.flori.photos.dto.UploadTargetResponse
import kr.ai.flori.photos.entity.PhotoCard
import kr.ai.flori.photos.entity.PhotoFile
import kr.ai.flori.photos.repository.PhotoCardRepository
import kr.ai.flori.sales.repository.SaleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 사진 카드 서비스. tags/photos는 jsonb·배열, 매출(sale_id) 연동.
 * presigned 업로드는 소유권/장수/이미지 메타 검증 후 발급. 모든 쿼리 TenantContext 격리(HARD).
 */
@Service
class PhotoCardService(
    private val photoCardRepository: PhotoCardRepository,
    private val s3PresignService: S3PresignService,
    private val saleRepository: SaleRepository,
) {
    @Transactional(readOnly = true)
    fun list(
        tag: String?,
        cursor: String?,
        customerId: String?,
    ): PhotoCardsPageResponse {
        val userId = TenantContext.currentUserId()
        val rows = photoCardRepository.findPage(userId, cursor, tag, customerId, PAGE_SIZE + 1)
        val hasMore = rows.size > PAGE_SIZE
        val cards = if (hasMore) rows.take(PAGE_SIZE) else rows
        val nextCursor = if (hasMore) cards.last().updatedAt else null
        return PhotoCardsPageResponse(cards.map(PhotoCardResponse::from), nextCursor, hasMore)
    }

    @Transactional(readOnly = true)
    fun get(id: Long): PhotoCardResponse = PhotoCardResponse.from(load(id))

    @Transactional(readOnly = true)
    fun getBySaleId(saleId: Long): PhotoCardResponse? =
        photoCardRepository
            .findFirstByUserIdAndSaleId(TenantContext.currentUserId(), saleId)
            ?.let(PhotoCardResponse::from)

    @Transactional
    fun create(request: PhotoCardCreateRequest): PhotoCardResponse {
        requirePhotoLimit(request.photos.size)
        val card = PhotoCard(TenantContext.currentUserId(), requireNotNull(request.title))
        card.description = request.description
        card.tags = request.tags.toTypedArray()
        card.photos = request.photos
        card.saleId = request.saleId?.also { verifySaleOwnership(it) }
        return PhotoCardResponse.from(photoCardRepository.save(card))
    }

    @Transactional
    fun update(
        id: Long,
        request: PhotoCardUpdateRequest,
    ): PhotoCardResponse {
        val card = load(id)
        request.title?.let { card.title = it }
        request.description?.let { card.description = it }
        request.tags?.let { card.tags = it.toTypedArray() }
        request.photos?.let {
            requirePhotoLimit(it.size)
            card.photos = it
        }
        request.saleId?.let {
            verifySaleOwnership(it)
            card.saleId = it
        }
        return PhotoCardResponse.from(photoCardRepository.save(card))
    }

    /** 삭제 후 정리 대상 사진 목록을 반환(스토리지 클린업은 호출측/후속 처리). */
    @Transactional
    fun delete(id: Long): List<PhotoFile> {
        val card = load(id)
        val photos = card.photos
        photoCardRepository.delete(card)
        return photos
    }

    @Transactional
    fun reorderPhotos(
        id: Long,
        photos: List<PhotoFile>,
    ): PhotoCardResponse {
        val card = load(id)
        card.photos = photos
        return PhotoCardResponse.from(photoCardRepository.save(card))
    }

    @Transactional
    fun deletePhoto(
        id: Long,
        photoUrl: String,
    ): PhotoCardResponse {
        val card = load(id)
        card.photos = card.photos.filterNot { it.url == photoUrl }
        return PhotoCardResponse.from(photoCardRepository.save(card))
    }

    /** presigned PUT 발급: 소유권 확인 + 카드당 최대 장수 + 이미지 메타 검증. */
    @Transactional(readOnly = true)
    fun createUploadTargets(
        cardId: Long,
        files: List<FileMetaRequest>,
    ): List<UploadTargetResponse> {
        val card = load(cardId)
        if (card.photos.size + files.size > MAX_PHOTOS_PER_CARD) {
            throw AppException(
                ErrorCode.VALIDATION,
                "사진은 최대 ${MAX_PHOTOS_PER_CARD}장까지 등록할 수 있습니다 (현재 ${card.photos.size}장)",
            )
        }
        return files.map { file ->
            val contentType = requireNotNull(file.type)
            validateImageMeta(contentType, file.size)
            val name = requireNotNull(file.name)
            val presigned = s3PresignService.presignUpload(buildKey(cardId, name), contentType)
            UploadTargetResponse(presigned.uploadUrl, presigned.fileUrl, name)
        }
    }

    private fun validateImageMeta(
        contentType: String,
        size: Long,
    ) {
        if (!contentType.startsWith("image/")) throw AppException(ErrorCode.VALIDATION, "이미지 파일만 업로드할 수 있습니다")
        if (size > MAX_FILE_SIZE_BYTES) throw AppException(ErrorCode.VALIDATION, "파일 크기가 너무 큽니다")
    }

    private fun buildKey(
        cardId: Long,
        name: String,
    ): String {
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "photo-cards/$cardId/${UUID.randomUUID()}-$safeName"
    }

    private fun requirePhotoLimit(count: Int) {
        if (count > MAX_PHOTOS_PER_CARD) {
            throw AppException(ErrorCode.VALIDATION, "사진은 최대 ${MAX_PHOTOS_PER_CARD}장까지 등록할 수 있습니다")
        }
    }

    private fun load(id: Long): PhotoCard =
        photoCardRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(ErrorCode.NOT_FOUND, "사진 카드를 찾을 수 없습니다")

    /** 매출 연동(sale_id) 소유권 검증 — 타 테넌트 매출 연결 차단. */
    private fun verifySaleOwnership(saleId: Long) {
        if (saleRepository.findByIdAndUserId(saleId, TenantContext.currentUserId()) == null) {
            throw AppException(ErrorCode.VALIDATION, "유효하지 않은 매출입니다")
        }
    }

    private companion object {
        const val PAGE_SIZE = 8
        const val MAX_PHOTOS_PER_CARD = 10
        const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024
    }
}
