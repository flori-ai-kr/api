package kr.ai.flori.common.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Sort

class PagingTest {
    @Test
    fun `offsetLimit - 기존 공식(offset÷limit)으로 페이지를 계산한다`() {
        val pageable = Paging.offsetLimit(offset = 40, limit = 20, maxLimit = 100)
        assertThat(pageable.pageNumber).isEqualTo(2)
        assertThat(pageable.pageSize).isEqualTo(20)
    }

    @Test
    fun `offsetLimit - 음수 offset은 0으로, limit은 1~maxLimit으로 보정한다`() {
        val pageable = Paging.offsetLimit(offset = -5, limit = 500, maxLimit = 100)
        assertThat(pageable.pageNumber).isEqualTo(0)
        assertThat(pageable.pageSize).isEqualTo(100)

        val minClamped = Paging.offsetLimit(offset = 0, limit = 0, maxLimit = 100)
        assertThat(minClamped.pageSize).isEqualTo(1)
    }

    @Test
    fun `offsetLimit - sort를 전달한다`() {
        val sort = Sort.by(Sort.Order.desc("date"))
        assertThat(Paging.offsetLimit(0, 10, 100, sort).sort).isEqualTo(sort)
        assertThat(Paging.offsetLimit(0, 10, 100).sort.isUnsorted).isTrue()
    }

    @Test
    fun `pageSize - 음수 page는 0으로, size는 1~maxSize로 보정한다`() {
        val pageable = Paging.pageSize(page = -1, size = 999, maxSize = 50)
        assertThat(pageable.pageNumber).isEqualTo(0)
        assertThat(pageable.pageSize).isEqualTo(50)

        val normal = Paging.pageSize(page = 3, size = 20, maxSize = 50, sort = Sort.by("id"))
        assertThat(normal.pageNumber).isEqualTo(3)
        assertThat(normal.pageSize).isEqualTo(20)
        assertThat(normal.sort).isEqualTo(Sort.by("id"))
    }
}
