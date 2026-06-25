package kr.ai.flori.billing.support

import kr.ai.flori.billing.repository.CouponRepository
import org.springframework.stereotype.Component
import java.security.SecureRandom

/** 쿠폰 코드 자동생성: 12자 Crockford Base32(I/L/O/U 제외), XXXX-XXXX-XXXX. 충돌 시 재생성. */
@Component
class CouponCodeGenerator(
    private val couponRepository: CouponRepository,
) {
    private val random = SecureRandom()

    fun generate(): String {
        repeat(MAX_TRIES) {
            val code = randomCode()
            if (!couponRepository.existsByCode(code)) return code
        }
        error("쿠폰 코드 생성 실패(충돌 과다)")
    }

    private fun randomCode(): String {
        val chars = CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }
        val raw = String(chars)
        return "${raw.substring(SEG_START, SEG_1)}-${raw.substring(SEG_1, SEG_2)}-${raw.substring(SEG_2, LENGTH)}"
    }

    private companion object {
        const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        const val LENGTH = 12
        const val SEG_START = 0
        const val SEG_1 = 4
        const val SEG_2 = 8
        const val MAX_TRIES = 10
    }
}
