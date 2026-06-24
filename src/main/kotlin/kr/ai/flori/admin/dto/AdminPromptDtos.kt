package kr.ai.flori.admin.dto

import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.Instant

/** 목록용 요약(본문 제외 — 채널별 버전 리스트). */
data class PromptSummary(
    val id: Long,
    val channel: String,
    val version: String,
    val isActive: Boolean,
    val model: String?,
    val temperature: BigDecimal?,
    val maxTokens: Int?,
    val notes: String?,
    val createdBy: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** 상세/편집용 — 본문 3조각 전문 포함. */
data class PromptDetail(
    val id: Long,
    val channel: String,
    val version: String,
    val isActive: Boolean,
    val systemMd: String,
    val rulesMd: String,
    val outputSpecMd: String,
    val model: String?,
    val temperature: BigDecimal?,
    val maxTokens: Int?,
    val notes: String?,
    val createdBy: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * 생성 요청. `fromId`가 있으면 해당 버전의 본문을 기본값으로 복제(clone)하고, 명시한 필드만 덮어쓴다.
 * `activate=true`면 생성 직후 활성화한다(같은 채널 기존 active 비활성).
 */
data class PromptCreateRequest(
    val channel: String = "blog",
    @field:NotBlank(message = "버전은 필수입니다")
    val version: String,
    val systemMd: String? = null,
    val rulesMd: String? = null,
    val outputSpecMd: String? = null,
    val model: String? = null,
    val temperature: BigDecimal? = null,
    val maxTokens: Int? = null,
    val notes: String? = null,
    val fromId: Long? = null,
    val activate: Boolean = false,
)

/** 부분 수정. null 필드는 변경하지 않는다. `isActive=true` 전환 시 활성화 트랜잭션을 탄다. */
data class PromptUpdateRequest(
    val version: String? = null,
    val systemMd: String? = null,
    val rulesMd: String? = null,
    val outputSpecMd: String? = null,
    val model: String? = null,
    val temperature: BigDecimal? = null,
    val maxTokens: Int? = null,
    val notes: String? = null,
    val isActive: Boolean? = null,
)
