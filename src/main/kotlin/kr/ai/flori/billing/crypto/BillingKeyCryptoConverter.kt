package kr.ai.flori.billing.crypto

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * billingKey 컬럼 가역 암호화(AES-256-GCM). 단방향 해시 금지 — 매 결제 시 원문 필요.
 * 키는 BILLING_ENCRYPTION_KEY(32바이트의 base64) 환경변수에서 주입.
 * 저장형식: base64(IV(12B) || ciphertext+tag). IV는 매번 랜덤(같은 평문도 다른 암호문).
 *
 * Spring Boot가 @Component 컨버터를 Hibernate에 주입한다(스프링 관리 빈).
 */
@Component
@Converter
class BillingKeyCryptoConverter(
    @Value("\${billing.encryption-key}") encryptionKeyBase64: String,
    @Value("\${spring.profiles.active:local}") activeProfile: String = "local",
) : AttributeConverter<String, String> {
    init {
        val decoded = Base64.getDecoder().decode(encryptionKeyBase64)
        require(decoded.size == KEY_BYTES) {
            "BILLING_ENCRYPTION_KEY는 32바이트(AES-256)여야 합니다"
        }
        // 운영 안전장치: 비-로컬 프로필에서 깃에 박힌 기본 암호화 키 사용 시 부팅 실패
        val activeProfiles = activeProfile.split(",").map { it.trim() }.toSet()
        require(activeProfiles.any { it in LOCAL_PROFILES } || encryptionKeyBase64 != DEV_DEFAULT_ENCRYPTION_KEY) {
            "운영 환경(profile=$activeProfile)에서 기본 BILLING_ENCRYPTION_KEY를 사용할 수 없습니다. BILLING_ENCRYPTION_KEY 환경변수를 설정하세요."
        }
    }

    private val key = SecretKeySpec(Base64.getDecoder().decode(encryptionKeyBase64), "AES")
    private val random = SecureRandom()

    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute == null) return null
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(attribute.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ct)
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData == null) return null
        val all = Base64.getDecoder().decode(dbData)
        val iv = all.copyOfRange(0, IV_BYTES)
        val ct = all.copyOfRange(IV_BYTES, all.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BYTES = 32
        const val DEV_DEFAULT_ENCRYPTION_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        val LOCAL_PROFILES = setOf("local", "test", "default")
    }
}
