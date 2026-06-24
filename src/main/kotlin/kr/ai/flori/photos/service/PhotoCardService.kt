package kr.ai.flori.photos.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.storage.S3PresignService
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.customers.repository.CustomerRepository
import kr.ai.flori.photos.dto.FileMetaRequest
import kr.ai.flori.photos.dto.PhotoCardCreateRequest
import kr.ai.flori.photos.dto.PhotoCardResponse
import kr.ai.flori.photos.dto.PhotoCardUpdateRequest
import kr.ai.flori.photos.dto.PhotoCardsPageResponse
import kr.ai.flori.photos.dto.UploadTargetResponse
import kr.ai.flori.photos.entity.PhotoCard
import kr.ai.flori.photos.entity.PhotoFile
import kr.ai.flori.photos.repository.PhotoCardRepository
import kr.ai.flori.sales.entity.Sale
import kr.ai.flori.sales.repository.SaleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 사진 카드 서비스. tags/photos는 jsonb·배열, 매출(sale_id) 연동.
 * presigned 업로드는 소유권/장수/이미지 메타 검증 후 발급. 모든 쿼리 TenantContext 격리(HARD).
 */
@Service
@Suppress("TooManyFunctions")
class PhotoCardService(
    private val photoCardRepository: PhotoCardRepository,
    private val s3PresignService: S3PresignService,
    private val saleRepository: SaleRepository,
    private val customerRepository: CustomerRepository,
) {
    @Transactional(readOnly = true)
    fun list(
        tag: String?,
        cursor: String?,
        customerId: String?,
        from: String? = null,
        to: String? = null,
    ): PhotoCardsPageResponse {
        val userId = TenantContext.currentUserId()
        // 복합 커서 "updatedAt|id" 파싱(구형=ts만 → id MAX로 같은 ts 전부 포함, 프론트가 dedupe).
        val sep = cursor?.lastIndexOf('|') ?: -1
        val cursorTs = cursor?.let { if (sep >= 0) it.substring(0, sep) else it }
        val cursorId = (cursor?.takeIf { sep >= 0 }?.substring(sep + 1)?.toLongOrNull()) ?: Long.MAX_VALUE
        val rows = photoCardRepository.findPage(userId, cursorTs, cursorId, tag, customerId, from, to, PAGE_SIZE + 1)
        val hasMore = rows.size > PAGE_SIZE
        val cards = if (hasMore) rows.take(PAGE_SIZE) else rows
        val nextCursor = if (hasMore) "${cards.last().updatedAt}|${cards.last().id}" else null
        // N+1 회피: 페이지의 distinct customerId 들을 1쿼리로 이름 맵 조회.
        val nameMap = customerNames(userId, cards.mapNotNull { it.customerId }.toSet())
        // 상단 요약 헤더용 총계(현재 필터 기준, 커서 무관). 네이티브 단일행 → List<Object[]>.
        // 집계 쿼리는 항상 1행이지만 방어적으로 firstOrNull 처리.
        val totals = photoCardRepository.countTotals(userId, tag, customerId, from, to).firstOrNull()
        return PhotoCardsPageResponse(
            cards.map { PhotoCardResponse.from(it, it.customerId?.let(nameMap::get)) },
            nextCursor,
            hasMore,
            totalCards = (totals?.get(0) as? Number)?.toLong() ?: 0L,
            totalPhotos = (totals?.get(1) as? Number)?.toLong() ?: 0L,
        )
    }

    @Transactional(readOnly = true)
    fun get(id: Long): PhotoCardResponse = toResponse(load(id))

    @Transactional(readOnly = true)
    fun getBySaleId(saleId: Long): PhotoCardResponse? =
        photoCardRepository
            .findFirstByUserIdAndSaleId(TenantContext.currentUserId(), saleId)
            ?.let(::toResponse)

    @Transactional
    fun create(request: PhotoCardCreateRequest): PhotoCardResponse {
        requirePhotoLimit(request.photos.size)
        requireTagLimit(request.tags.size)
        val card = PhotoCard(TenantContext.currentUserId(), requireNotNull(request.title))
        card.memo = request.memo
        card.tags = request.tags.toTypedArray()
        card.photos = request.photos
        val sale = request.saleId?.let { requireSale(it) }
        card.saleId = sale?.id
        // 고객 연결: 명시한 customerId가 우선, 없으면 연동 매출의 고객을 상속한다.
        // (사진첩 고객 필터·고객 상세 집계가 photo_cards.customer_id 만 보므로, 매출/캘린더 플로우처럼
        //  saleId 만 오는 경우에도 고객 연결이 누락되지 않게 한다.)
        card.customerId = request.customerId?.also { verifyCustomerOwnership(it) } ?: sale?.customerId
        return toResponse(photoCardRepository.save(card))
    }

    @Transactional
    fun update(
        id: Long,
        request: PhotoCardUpdateRequest,
    ): PhotoCardResponse {
        val card = load(id)
        request.title?.let { card.title = it }
        request.memo?.let { card.memo = it }
        request.tags?.let {
            requireTagLimit(it.size)
            card.tags = it.toTypedArray()
        }
        request.photos?.let {
            requirePhotoLimit(it.size)
            card.photos = it
        }
        val requestSale = request.saleId?.let { requireSale(it) }
        requestSale?.let { card.saleId = it.id }
        // saleId 와 동일하게 null=미변경. 연결 해제는 clearCustomer 플래그로만 처리.
        if (request.clearCustomer) {
            card.customerId = null
        } else if (request.customerId != null) {
            verifyCustomerOwnership(request.customerId)
            card.customerId = request.customerId
        } else if (card.customerId == null) {
            // 고객 지정/해제 신호가 없고 아직 미연결이면, 연동 매출의 고객을 상속(과거 데이터·매출 플로우 백필).
            // 이미 고객이 연결된 카드는 건드리지 않는다 — 사진첩에서 수동 연결한 고객을 매출 고객으로 덮어쓰지 않기 위함.
            // 이번 요청으로 검증·조회한 sale을 우선 재사용하고, 없으면 카드의 기존 sale_id로 조회(2차 쿼리 회피).
            val sale = requestSale ?: card.saleId?.let { findSale(it) }
            card.customerId = sale?.customerId
        }
        return toResponse(photoCardRepository.save(card))
    }

    /** 카드 삭제 + 연결된 S3 객체 정리(best-effort). */
    @Transactional
    fun delete(id: Long) {
        val card = load(id)
        card.photos.forEach { s3PresignService.deleteByUrl(it.url) }
        photoCardRepository.delete(card)
    }

    /** 원본 다운로드용 presigned GET URL. 카드 소유권 + 해당 사진이 이 카드에 속하는지 검증 후 발급. */
    @Transactional(readOnly = true)
    fun downloadUrl(
        id: Long,
        photoUrl: String,
    ): String {
        val card = load(id)
        val photo =
            card.photos.firstOrNull { it.url == photoUrl }
                ?: throw AppException(CommonErrorCode.NOT_FOUND, "사진을 찾을 수 없습니다")
        return s3PresignService.presignDownload(photoUrl, photo.originalName)
    }

    @Transactional
    fun reorderPhotos(
        id: Long,
        photos: List<PhotoFile>,
    ): PhotoCardResponse {
        val card = load(id)
        card.photos = photos
        return toResponse(photoCardRepository.save(card))
    }

    @Transactional
    fun deletePhoto(
        id: Long,
        photoUrl: String,
    ): PhotoCardResponse {
        val card = load(id)
        // 단건 삭제도 S3 객체까지 정리(전체 카드 삭제와 동일) — 안 그러면 CloudFront에 고아 객체가 공개로 남는다.
        if (card.photos.any { it.url == photoUrl }) s3PresignService.deleteByUrl(photoUrl)
        card.photos = card.photos.filterNot { it.url == photoUrl }
        return toResponse(photoCardRepository.save(card))
    }

    /**
     * 카드 생성 전(신규) presigned PUT 발급. 카드가 아직 없으므로 userId 기준 키로 발급한다.
     * 업로드 성공 후 imageUrls를 담아 카드를 생성하면, 업로드 실패 시 DB에 카드가 남지 않는다.
     */
    fun createUploadTargets(files: List<FileMetaRequest>): List<UploadTargetResponse> {
        val userId = TenantContext.currentUserId()
        requirePhotoLimit(files.size)
        return files.map { file ->
            val contentType = requireNotNull(file.type)
            validateImageMeta(contentType, file.size)
            val name = requireNotNull(file.name)
            val presigned = s3PresignService.presignUpload(buildUserKey(userId, name), contentType)
            UploadTargetResponse(presigned.uploadUrl, presigned.fileUrl, name)
        }
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
                CommonErrorCode.VALIDATION,
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
        // SVG 등 스크립트 내장 가능 타입을 막기 위해 prefix가 아닌 명시 허용 목록으로 검증.
        if (contentType.lowercase() !in ALLOWED_IMAGE_TYPES) {
            throw AppException(CommonErrorCode.VALIDATION, "지원하지 않는 이미지 형식입니다")
        }
        if (size > MAX_FILE_SIZE_BYTES) throw AppException(CommonErrorCode.VALIDATION, "파일 크기가 너무 큽니다")
    }

    private fun buildKey(
        cardId: Long,
        name: String,
    ): String {
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "photo-cards/$cardId/${UUID.randomUUID()}-$safeName"
    }

    private fun buildUserKey(
        userId: Long,
        name: String,
    ): String {
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "photo-cards/u$userId/${UUID.randomUUID()}-$safeName"
    }

    private fun requirePhotoLimit(count: Int) {
        if (count > MAX_PHOTOS_PER_CARD) {
            throw AppException(CommonErrorCode.VALIDATION, "사진은 최대 ${MAX_PHOTOS_PER_CARD}장까지 등록할 수 있습니다")
        }
    }

    private fun requireTagLimit(count: Int) {
        if (count > MAX_TAGS_PER_CARD) {
            throw AppException(CommonErrorCode.VALIDATION, "태그는 최대 ${MAX_TAGS_PER_CARD}개까지 등록할 수 있습니다")
        }
    }

    private fun load(id: Long): PhotoCard =
        photoCardRepository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(CommonErrorCode.NOT_FOUND, "사진 카드를 찾을 수 없습니다")

    /** 매출 연동(sale_id) 소유권 검증 후 매출 반환 — 타 테넌트 매출 연결 차단 + 고객 상속에 사용. */
    private fun requireSale(saleId: Long): Sale =
        saleRepository.findByIdAndUserId(saleId, TenantContext.currentUserId())
            ?: throw AppException(CommonErrorCode.VALIDATION, "유효하지 않은 매출입니다")

    /** 매출 조회(테넌트 격리, 없으면 null). 이미 저장된 card.saleId 처럼 throw 없이 고객을 상속받을 때 사용. */
    private fun findSale(saleId: Long): Sale? = saleRepository.findByIdAndUserId(saleId, TenantContext.currentUserId())

    /** 고객 연동(customer_id) 소유권 검증 — 타 테넌트 고객 연결 차단. */
    private fun verifyCustomerOwnership(customerId: Long) {
        if (customerRepository.findByIdAndUserId(customerId, TenantContext.currentUserId()) == null) {
            throw AppException(CommonErrorCode.VALIDATION, "유효하지 않은 고객입니다")
        }
    }

    /** 단건 응답: 연결 고객명(테넌트 스코프) 해석해 조립. */
    private fun toResponse(card: PhotoCard): PhotoCardResponse {
        val name =
            card.customerId?.let {
                customerRepository.findByIdAndUserId(it, TenantContext.currentUserId())?.name
            }
        return PhotoCardResponse.from(card, name)
    }

    /** customerId → 고객명 맵(테넌트 1쿼리). 빈 입력이면 빈 맵. */
    private fun customerNames(
        userId: Long,
        ids: Set<Long>,
    ): Map<Long, String> =
        if (ids.isEmpty()) {
            emptyMap()
        } else {
            customerRepository.findByUserIdAndIdIn(userId, ids).associate { requireNotNull(it.id) to it.name }
        }

    private companion object {
        const val PAGE_SIZE = 8
        const val MAX_PHOTOS_PER_CARD = 10
        const val MAX_TAGS_PER_CARD = 3
        const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024
        val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/avif", "image/heic")
    }
}
