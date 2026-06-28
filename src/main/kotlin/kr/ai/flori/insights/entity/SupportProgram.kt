package kr.ai.flori.insights.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity
import java.time.Instant
import java.time.LocalDate

/**
 * 지원사업(소진공·기업마당·K-Startup 큐레이션). 공유 읽기 테이블 — user_id 격리 대상이 아니다.
 *
 * dDay(마감까지 남은 일수)는 apply_end 로 응답 DTO에서 파생 계산한다.
 * append-only 큐레이션이라 updated_at 없음(collected_at 만) → BaseCreatedEntity.
 */
@Entity
@Table(name = "support_programs")
class SupportProgram(
    @Column(name = "source", nullable = false)
    var source: String,
    @Column(name = "source_id", nullable = false)
    var sourceId: String,
    @Column(name = "title", nullable = false)
    var title: String,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "agency")
    var agency: String? = null

    @Column(name = "category")
    var category: String? = null

    @Column(name = "target")
    var target: String? = null

    @Column(name = "summary")
    var summary: String? = null

    @Column(name = "apply_start")
    var applyStart: LocalDate? = null

    @Column(name = "apply_end")
    var applyEnd: LocalDate? = null

    @Column(name = "source_url")
    var sourceUrl: String? = null

    @Column(name = "collected_at")
    var collectedAt: Instant? = null
}
