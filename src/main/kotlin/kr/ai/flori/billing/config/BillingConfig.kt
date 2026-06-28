package kr.ai.flori.billing.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/** 빌링 모듈 설정. TossPaymentsProperties 바인딩 활성화. */
@Configuration
@EnableConfigurationProperties(TossPaymentsProperties::class)
class BillingConfig(
    private val tossProperties: TossPaymentsProperties,
    @Value("\${spring.profiles.active:local}") private val activeProfile: String,
) {
    // 운영 안전장치: 비-로컬 프로필에서 깃에 박힌 기본 토스 시크릿 사용 시 부팅 실패
    init {
        val activeProfiles = activeProfile.split(",").map { it.trim() }.toSet()
        require(activeProfiles.any { it in LOCAL_PROFILES } || tossProperties.secretKey != DEV_DEFAULT_TOSS_SECRET_KEY) {
            "운영 환경(profile=$activeProfile)에서 기본 TOSS_SECRET_KEY를 사용할 수 없습니다. TOSS_SECRET_KEY 환경변수를 설정하세요."
        }
    }

    @Bean
    fun tossRestClient(properties: TossPaymentsProperties): RestClient =
        RestClient
            .builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(properties.connectTimeoutMs)
                    setReadTimeout(properties.readTimeoutMs)
                },
            ).build()

    private companion object {
        const val DEV_DEFAULT_TOSS_SECRET_KEY = "test_sk_REPLACE_WITH_SANDBOX_KEY"
        val LOCAL_PROFILES = setOf("local", "test", "default")
    }
}
