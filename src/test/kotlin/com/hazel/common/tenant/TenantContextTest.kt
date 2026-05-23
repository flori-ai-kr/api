package com.hazel.common.tenant

import com.hazel.common.error.AppException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID

class TenantContextTest {
    @AfterEach
    fun tearDown() {
        TenantContext.clear()
    }

    @Test
    fun `set 후 currentUserId가 동일 값을 반환한다`() {
        val id = UUID.randomUUID()
        TenantContext.set(id)
        assertThat(TenantContext.currentUserId()).isEqualTo(id)
    }

    @Test
    fun `미설정 시 currentUserId는 인증 예외를 던진다`() {
        TenantContext.clear()
        assertThatThrownBy { TenantContext.currentUserId() }.isInstanceOf(AppException::class.java)
    }
}
