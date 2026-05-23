package com.hazel.common.security

import com.hazel.common.error.AppException
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class InternalAuthVerifierTest {
    private val verifier = InternalAuthVerifier("super-secret-internal-key")

    @Test
    fun `올바른 Bearer 키는 통과한다`() {
        assertThatCode { verifier.verify("Bearer super-secret-internal-key") }.doesNotThrowAnyException()
    }

    @Test
    fun `틀린 키·누락은 거부된다`() {
        assertThatThrownBy { verifier.verify("Bearer wrong") }.isInstanceOf(AppException::class.java)
        assertThatThrownBy { verifier.verify(null) }.isInstanceOf(AppException::class.java)
        assertThatThrownBy { verifier.verify("super-secret-internal-key") }.isInstanceOf(AppException::class.java)
    }

    @Test
    fun `키 미설정 환경은 항상 거부된다`() {
        val blank = InternalAuthVerifier("")
        assertThatThrownBy { blank.verify("Bearer anything") }.isInstanceOf(AppException::class.java)
    }
}
