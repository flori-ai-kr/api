package kr.ai.flori.common.storage

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration

/**
 * S3 presigned PUT URL 발급. 앱이 이 URL로 직접 업로드하고, 조회는 CloudFront URL로.
 * 소유권/메타 검증과 키 생성 규칙은 사진 도메인(SPEC-010)에서 이 서비스를 호출하며 적용한다.
 */
@Service
class S3PresignService(
    private val presigner: S3Presigner,
    private val properties: StorageProperties,
) {
    fun presignUpload(
        key: String,
        contentType: String,
    ): PresignedUpload {
        if (properties.s3.bucket.isBlank()) {
            throw AppException(ErrorCode.INTERNAL, "스토리지가 구성되지 않았습니다")
        }
        val putRequest =
            PutObjectRequest
                .builder()
                .bucket(properties.s3.bucket)
                .key(key)
                .contentType(contentType)
                .build()
        val presignRequest =
            PutObjectPresignRequest
                .builder()
                .signatureDuration(Duration.ofSeconds(properties.s3.presignExpirySeconds))
                .putObjectRequest(putRequest)
                .build()

        val uploadUrl = presigner.presignPutObject(presignRequest).url().toString()
        return PresignedUpload(
            uploadUrl = uploadUrl,
            fileUrl = publicUrl(key),
            expiresInSeconds = properties.s3.presignExpirySeconds,
        )
    }

    private fun publicUrl(key: String): String =
        if (properties.cloudfront.domain.isNotBlank()) {
            "https://${properties.cloudfront.domain}/$key"
        } else {
            "https://${properties.s3.bucket}.s3.${properties.region}.amazonaws.com/$key"
        }
}

data class PresignedUpload(
    val uploadUrl: String,
    val fileUrl: String,
    val expiresInSeconds: Long,
)
