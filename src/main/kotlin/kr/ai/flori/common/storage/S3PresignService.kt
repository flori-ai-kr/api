package kr.ai.flori.common.storage

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI
import java.time.Duration

/**
 * S3 스토리지 연동. 앱은 presigned PUT URL로 직접 업로드, 조회는 CloudFront URL로.
 * 다운로드(원본 저장)는 presigned GET, 삭제는 서버가 S3 객체를 직접 제거한다.
 * 소유권/메타 검증과 키 생성 규칙은 사진 도메인(SPEC-010)에서 이 서비스를 호출하며 적용한다.
 */
@Service
class S3PresignService(
    private val presigner: S3Presigner,
    private val s3Client: S3Client,
    private val properties: StorageProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun presignUpload(
        key: String,
        contentType: String,
    ): PresignedUpload {
        requireBucket()
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

    /**
     * 원본 다운로드용 presigned GET URL. 공개 URL(CloudFront 등)에서 객체 키를 역산해 서명한다.
     * fileName이 있으면 Content-Disposition: attachment 로 다운로드 파일명을 지정한다.
     */
    fun presignDownload(
        fileUrl: String,
        fileName: String? = null,
    ): String {
        requireBucket()
        val getRequestBuilder =
            GetObjectRequest
                .builder()
                .bucket(properties.s3.bucket)
                .key(keyFromUrl(fileUrl))
        if (!fileName.isNullOrBlank()) {
            // 헤더 인젝션 방지: 따옴표/개행 제거 후 Content-Disposition에 사용.
            val safeName = fileName.replace(Regex("[\"\\r\\n]"), "")
            getRequestBuilder.responseContentDisposition("attachment; filename=\"$safeName\"")
        }
        val presignRequest =
            GetObjectPresignRequest
                .builder()
                .signatureDuration(Duration.ofSeconds(properties.s3.presignExpirySeconds))
                .getObjectRequest(getRequestBuilder.build())
                .build()
        return presigner.presignGetObject(presignRequest).url().toString()
    }

    /**
     * 공개 URL에 해당하는 S3 객체를 삭제한다. 스토리지 미구성(버킷 공백) 시 no-op.
     * 삭제 실패가 상위 작업(DB 정리)을 막지 않도록 best-effort(로깅 후 무시).
     */
    @Suppress("TooGenericExceptionCaught")
    fun deleteByUrl(fileUrl: String) {
        if (properties.s3.bucket.isBlank()) return
        try {
            s3Client.deleteObject(
                DeleteObjectRequest
                    .builder()
                    .bucket(properties.s3.bucket)
                    .key(keyFromUrl(fileUrl))
                    .build(),
            )
        } catch (e: Exception) {
            // 잘못된 URL(URISyntaxException)·SDK 오류 모두 무시 — 삭제 실패가 DB 정리를 막지 않게(best-effort).
            log.warn("S3 객체 삭제 실패(무시) url={}", fileUrl, e)
        }
    }

    private fun requireBucket() {
        if (properties.s3.bucket.isBlank()) {
            throw AppException(CommonErrorCode.INTERNAL, "스토리지가 구성되지 않았습니다")
        }
    }

    /**
     * 공개 URL(CloudFront/S3)에서 객체 키를 역산. 호스트 이후 경로에서 선행 '/' 제거.
     * 허용 prefix(photo-cards/·community/)만 통과시켜 임의 키 서명/삭제(타 테넌트 객체 접근)를 차단한다.
     */
    private fun keyFromUrl(fileUrl: String): String {
        val key =
            URI(fileUrl).path.removePrefix("/").ifBlank {
                throw AppException(CommonErrorCode.VALIDATION, "유효하지 않은 파일 URL입니다")
            }
        if (ALLOWED_KEY_PREFIXES.none { key.startsWith(it) } || key.contains("..")) {
            throw AppException(CommonErrorCode.VALIDATION, "유효하지 않은 파일 URL입니다")
        }
        return key
    }

    private fun publicUrl(key: String): String =
        if (properties.cloudfront.domain.isNotBlank()) {
            "https://${properties.cloudfront.domain}/$key"
        } else {
            "https://${properties.s3.bucket}.s3.${properties.region}.amazonaws.com/$key"
        }

    private companion object {
        val ALLOWED_KEY_PREFIXES = listOf("photo-cards/", "community/", "business-licenses/", "profiles/")
    }
}

data class PresignedUpload(
    val uploadUrl: String,
    val fileUrl: String,
    val expiresInSeconds: Long,
)
