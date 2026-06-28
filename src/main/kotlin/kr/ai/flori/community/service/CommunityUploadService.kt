package kr.ai.flori.community.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.storage.S3PresignService
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.community.dto.CommunityFileMetaRequest
import kr.ai.flori.community.dto.CommunityUploadTargetResponse
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 커뮤니티 이미지 업로드 타깃(presigned PUT) 발급 전담 서비스.
 * 브라우저가 S3로 직접 PUT 하도록 presigned URL을 내려준다(Server Action 본문 크기 제한 우회).
 */
@Service
class CommunityUploadService(
    private val s3PresignService: S3PresignService,
) {
    fun createUploadTargets(files: List<CommunityFileMetaRequest>): List<CommunityUploadTargetResponse> {
        val userId = TenantContext.currentUserId()
        if (files.size > MAX_FILES_PER_REQUEST) {
            throw AppException(CommonErrorCode.VALIDATION, "한 번에 최대 ${MAX_FILES_PER_REQUEST}장까지 업로드할 수 있습니다")
        }
        return files.map { file ->
            val contentType = requireNotNull(file.type)
            validateImageMeta(contentType, file.size)
            val name = requireNotNull(file.name)
            val presigned = s3PresignService.presignUpload(buildKey(userId, name), contentType)
            CommunityUploadTargetResponse(presigned.uploadUrl, presigned.fileUrl, name)
        }
    }

    private fun validateImageMeta(
        contentType: String,
        size: Long,
    ) {
        // SVG 등 스크립트 내장 가능 타입 차단을 위해 prefix가 아닌 명시 허용 목록으로 검증.
        if (contentType.lowercase() !in ALLOWED_IMAGE_TYPES) {
            throw AppException(CommonErrorCode.VALIDATION, "지원하지 않는 이미지 형식입니다")
        }
        if (size > MAX_FILE_SIZE_BYTES) {
            throw AppException(CommonErrorCode.VALIDATION, "파일 크기가 너무 큽니다")
        }
    }

    private fun buildKey(
        userId: Long,
        name: String,
    ): String {
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "community/$userId/${UUID.randomUUID()}-$safeName"
    }

    private companion object {
        const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024
        const val MAX_FILES_PER_REQUEST = 10
        val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/avif", "image/heic")
    }
}
