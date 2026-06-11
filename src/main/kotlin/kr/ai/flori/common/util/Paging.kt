package kr.ai.flori.common.util

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

/**
 * 페이지네이션 파라미터 → Pageable 변환 SSOT.
 * 보정(coerce) 규칙과 offset→page 공식이 서비스마다 제각각 복붙되는 것을 막는다.
 * API 파라미터 형태(offset/limit 또는 page/size)는 엔드포인트 계약 그대로 두고 내부 변환만 통일한다.
 */
object Paging {
    /** 무한스크롤형(offset/limit). offset이 limit의 배수가 아니면 내림한 페이지로 근사한다(기존 규약 유지). */
    fun offsetLimit(
        offset: Int,
        limit: Int,
        maxLimit: Int,
        sort: Sort = Sort.unsorted(),
    ): Pageable {
        val safeLimit = limit.coerceIn(1, maxLimit)
        val safeOffset = offset.coerceAtLeast(0)
        return PageRequest.of(safeOffset / safeLimit, safeLimit, sort)
    }

    /** 페이지형(page/size). */
    fun pageSize(
        page: Int,
        size: Int,
        maxSize: Int,
        sort: Sort = Sort.unsorted(),
    ): Pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, maxSize), sort)
}
