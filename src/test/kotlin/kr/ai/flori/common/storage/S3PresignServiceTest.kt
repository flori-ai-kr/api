package kr.ai.flori.common.storage

import kr.ai.flori.common.error.AppException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * presign은 로컬 서명 연산이라 네트워크/실제 자격증명 없이 검증 가능.
 */
class S3PresignServiceTest {
    private val presigner: S3Presigner =
        S3Presigner
            .builder()
            .region(Region.AP_NORTHEAST_2)
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIATEST", "test-secret-key")),
            ).build()

    private val s3Client: S3Client =
        S3Client
            .builder()
            .region(Region.AP_NORTHEAST_2)
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIATEST", "test-secret-key")),
            ).build()

    private fun service(
        bucket: String = "flori-test-bucket",
        cloudfront: String = "cdn.flori.dev",
    ) = S3PresignService(
        presigner,
        s3Client,
        StorageProperties(
            region = "ap-northeast-2",
            s3 = StorageProperties.S3(bucket = bucket, presignExpirySeconds = 300),
            cloudfront = StorageProperties.CloudFront(domain = cloudfront),
        ),
    )

    @Test
    fun `presigned PUT URL과 CloudFront 파일 URL을 발급한다`() {
        val result = service().presignUpload("photos/u1/abc.jpg", "image/jpeg")

        assertThat(result.uploadUrl).contains("flori-test-bucket")
        assertThat(result.uploadUrl).contains("photos/u1/abc.jpg")
        assertThat(result.uploadUrl).contains("X-Amz-Signature")
        assertThat(result.fileUrl).isEqualTo("https://cdn.flori.dev/photos/u1/abc.jpg")
        assertThat(result.expiresInSeconds).isEqualTo(300)
    }

    @Test
    fun `CloudFront 미설정 시 S3 객체 URL을 반환한다`() {
        val result = service(cloudfront = "").presignUpload("k.png", "image/png")
        assertThat(result.fileUrl).isEqualTo("https://flori-test-bucket.s3.ap-northeast-2.amazonaws.com/k.png")
    }

    @Test
    fun `버킷 미설정 시 예외를 던진다`() {
        assertThatThrownBy { service(bucket = "").presignUpload("k.png", "image/png") }
            .isInstanceOf(AppException::class.java)
    }

    @Test
    fun `presigned GET URL을 발급한다(공개 URL에서 키 역산)`() {
        val url = service().presignDownload("https://cdn.flori.dev/photo-cards/1/abc.jpg", "abc.jpg")

        assertThat(url).contains("flori-test-bucket")
        assertThat(url).contains("photo-cards/1/abc.jpg")
        assertThat(url).contains("X-Amz-Signature")
    }

    @Test
    fun `버킷 미설정 시 삭제는 no-op(예외 없음)`() {
        service(bucket = "").deleteByUrl("https://cdn.flori.dev/photo-cards/1/abc.jpg")
    }
}
