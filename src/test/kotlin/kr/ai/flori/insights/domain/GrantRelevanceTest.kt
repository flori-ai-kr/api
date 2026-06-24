package kr.ai.flori.insights.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 지원사업 소상공인(꽃집) 관련도 필터 검증.
 * 두 소스(K-Startup·기업마당) 모두 전국·전분야 공고를 주므로, 수집기는 소상공인/화훼 관련만 적재한다.
 */
class GrantRelevanceTest {
    @Test
    fun `소상공인 관련 키워드가 있으면 관련 공고다`() {
        assertThat(GrantRelevance.isRelevant("2026년 소상공인 온라인판로 지원사업", null, null)).isTrue()
        assertThat(GrantRelevance.isRelevant("전통시장 활성화 사업", null, null)).isTrue()
        assertThat(GrantRelevance.isRelevant(null, "자영업자 대상 컨설팅", null)).isTrue()
        assertThat(GrantRelevance.isRelevant("백년가게 육성", "골목상권", "소공인")).isTrue()
    }

    @Test
    fun `화훼·꽃 관련 키워드도 관련 공고다`() {
        assertThat(GrantRelevance.isRelevant("화훼 농가 경영안정 지원", null, null)).isTrue()
        assertThat(GrantRelevance.isRelevant("플로리스트 양성 과정", null, null)).isTrue()
        assertThat(GrantRelevance.isRelevant(null, null, "꽃집·화원 운영자")).isTrue()
    }

    @Test
    fun `소상공인·화훼와 무관하면 제외한다`() {
        assertThat(GrantRelevance.isRelevant("딥테크 특화 창업중심대학", "공고 내용", "일반기업")).isFalse()
        assertThat(GrantRelevance.isRelevant("반도체 소부장 R&D 지원", "기술개발", "중소기업")).isFalse()
        assertThat(GrantRelevance.isRelevant("섬유패션 원부자재 공동비축", null, "중소기업")).isFalse()
    }

    @Test
    fun `전부 비어있으면 제외한다`() {
        assertThat(GrantRelevance.isRelevant(null, null, null)).isFalse()
        assertThat(GrantRelevance.isRelevant("", "", "")).isFalse()
    }
}
