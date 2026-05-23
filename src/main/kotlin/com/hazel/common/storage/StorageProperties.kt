package com.hazel.common.storage

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * S3/CloudFront 스토리지 설정. 시크릿(자격증명)은 AWS 기본 자격증명 체인(환경변수)에서 해결.
 */
@ConfigurationProperties(prefix = "aws")
data class StorageProperties(
    val region: String = "ap-northeast-2",
    val s3: S3 = S3(),
    val cloudfront: CloudFront = CloudFront(),
) {
    data class S3(
        val bucket: String = "",
        val presignExpirySeconds: Long = 300,
    )

    data class CloudFront(
        val domain: String = "",
    )
}
