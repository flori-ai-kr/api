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
) : AttributeConverter<String, String> {
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
    }
}
