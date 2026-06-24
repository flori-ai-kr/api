package kr.ai.flori.billing.crypto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BillingKeyCryptoConverterTest {
    // 32바이트 키의 base64 (테스트 전용)
    private val converter = BillingKeyCryptoConverter("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")

    @Test
    fun `암호화 후 복호화하면 원문이 복원된다`() {
        val plain = "billing_key_abc123"
        val cipher = converter.convertToDatabaseColumn(plain)
        assertThat(cipher).isNotEqualTo(plain)
        assertThat(converter.convertToEntityAttribute(cipher)).isEqualTo(plain)
    }

    @Test
    fun `같은 평문도 매번 다른 암호문이 된다(IV 랜덤)`() {
        val plain = "billing_key_abc123"
        val a = converter.convertToDatabaseColumn(plain)
        val b = converter.convertToDatabaseColumn(plain)
        assertThat(a).isNotEqualTo(b)
        assertThat(converter.convertToEntityAttribute(a)).isEqualTo(plain)
        assertThat(converter.convertToEntityAttribute(b)).isEqualTo(plain)
    }

    @Test
    fun `null 은 null 로 통과한다`() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull()
        assertThat(converter.convertToEntityAttribute(null)).isNull()
    }
}
