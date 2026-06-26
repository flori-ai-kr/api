package kr.ai.flori.billing.support

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.Base64

/** 어뷰징 방어용 신원 해시. SHA-256(base64). 단방향. */
@Component
class IdentityHasher {
    fun hash(
        provider: String,
        providerId: String?,
    ): String = sha256Base64("$provider:$providerId")

    /**
     * 체험 1회 제한 키. 사업자등록번호(하이픈 제거 10자리) 기준.
     * 소셜 신원은 새 계정으로 우회 가능하지만 사업자번호는 실제 등록이 필요하므로 어뷰징에 더 강하다.
     */
    fun hashBusiness(businessNumber: String): String = sha256Base64("biz:$businessNumber")

    private fun sha256Base64(input: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(input.toByteArray()),
        )
}
