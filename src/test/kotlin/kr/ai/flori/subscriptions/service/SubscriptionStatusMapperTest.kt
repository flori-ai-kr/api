package kr.ai.flori.subscriptions.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 이벤트→상태 매핑(순수 함수) 단위 검증. SPEC-SERVER-014 상태 전이 규칙.
 */
class SubscriptionStatusMapperTest {
    @Test
    fun `구매·갱신·상품변경·취소복원은 active`() {
        listOf("INITIAL_PURCHASE", "RENEWAL", "PRODUCT_CHANGE", "UNCANCELLATION", "NON_RENEWING_PURCHASE").forEach {
            assertThat(SubscriptionStatusMapper.mapStatus(it)).isEqualTo("active")
        }
    }

    @Test
    fun `취소는 active를 유지한다(기간말 만료)`() {
        assertThat(SubscriptionStatusMapper.mapStatus("CANCELLATION")).isEqualTo("active")
    }

    @Test
    fun `결제이슈는 in_grace`() {
        assertThat(SubscriptionStatusMapper.mapStatus("BILLING_ISSUE")).isEqualTo("in_grace")
    }

    @Test
    fun `만료는 expired, 환불은 none`() {
        assertThat(SubscriptionStatusMapper.mapStatus("EXPIRATION")).isEqualTo("expired")
        assertThat(SubscriptionStatusMapper.mapStatus("REFUND")).isEqualTo("none")
    }

    @Test
    fun `소문자·미지정·알수없는 타입`() {
        assertThat(SubscriptionStatusMapper.mapStatus("renewal")).isEqualTo("active") // 대소문자 무관
        assertThat(SubscriptionStatusMapper.mapStatus("TRANSFER")).isNull() // 상태 변경 없음
        assertThat(SubscriptionStatusMapper.mapStatus(null)).isNull()
    }

    @Test
    fun `store 매핑은 apple 또는 google`() {
        assertThat(SubscriptionStatusMapper.mapStore("APP_STORE")).isEqualTo("apple")
        assertThat(SubscriptionStatusMapper.mapStore("MAC_APP_STORE")).isEqualTo("apple")
        assertThat(SubscriptionStatusMapper.mapStore("PLAY_STORE")).isEqualTo("google")
        assertThat(SubscriptionStatusMapper.mapStore(null)).isEqualTo("apple") // 기본값
    }
}
