package kr.ai.flori.common.storage

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * S3Presigner/S3Client 빈. 자격증명은 기본 체인(AWS_ACCESS_KEY_ID/SECRET 등)에서 지연 해결되므로
 * 자격증명 없이도 컨텍스트는 부팅된다(실제 presign/삭제 시점에 필요).
 */
@Configuration
@EnableConfigurationProperties(StorageProperties::class)
class S3Config(
    private val properties: StorageProperties,
) {
    @Bean
    fun s3Presigner(): S3Presigner =
        S3Presigner
            .builder()
            .region(Region.of(properties.region))
            .build()

    @Bean
    fun s3Client(): S3Client =
        S3Client
            .builder()
            .region(Region.of(properties.region))
            .build()
}
