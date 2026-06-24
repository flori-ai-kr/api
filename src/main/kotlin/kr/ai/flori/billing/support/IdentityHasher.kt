package kr.ai.flori.billing.support

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.Base64

/** 어뷰징 방어용 신원 해시. SHA-256(base64) of "provider:providerId". 단방향. */
@Component
class IdentityHasher {
    fun hash(
        provider: String,
        providerId: String?,
    ): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest("$provider:$providerId".toByteArray()),
        )
}
