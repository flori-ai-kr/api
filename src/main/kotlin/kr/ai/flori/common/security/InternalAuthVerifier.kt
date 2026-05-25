package kr.ai.flori.common.security

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 내부 API(수집/브로드캐스트) Bearer 인증. 타이밍-세이프 비교는 [BearerSecret] 공통 유틸 사용.
 * 키 미설정 시 모든 내부 호출을 차단(안전한 기본값).
 */
@Component
class InternalAuthVerifier(
    @Value("\${internal.api-key:}") private val apiKey: String,
) {
    fun verify(authorizationHeader: String?) {
        if (!BearerSecret.matches(BearerSecret.extract(authorizationHeader), apiKey)) {
            throw AppException(ErrorCode.UNAUTHORIZED, "내부 API 인증 실패")
        }
    }
}
