package kr.ai.flori.admin.service

import kr.ai.flori.admin.domain.PromptModels
import kr.ai.flori.admin.dto.PromptCreateRequest
import kr.ai.flori.admin.dto.PromptDetail
import kr.ai.flori.admin.dto.PromptSummary
import kr.ai.flori.admin.dto.PromptUpdateRequest
import kr.ai.flori.admin.error.AdminErrorCode
import kr.ai.flori.ai.entity.AiPrompt
import kr.ai.flori.ai.repository.AiPromptRepository
import kr.ai.flori.ai.service.PromptResolver
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * AI 프롬프트 레지스트리 운영(SPEC-AI-008). 슈퍼어드민 콘솔(@RequiresAdmin)의 CRUD·버전·활성화를 담당한다.
 *
 * 불변식: 채널당 active 1개([activate]가 같은 채널 기존 active를 비활성). active 버전은 삭제 거부.
 * 모델은 화이트리스트만 허용. 활성/수정/삭제 후 게이트웨이 [PromptResolver] 캐시를 무효화한다.
 */
@Service
class AdminPromptService(
    private val repository: AiPromptRepository,
    private val promptResolver: PromptResolver,
    private val audit: AdminAuditService,
) {
    @Transactional(readOnly = true)
    fun list(channel: String): List<PromptSummary> {
        validateChannel(channel)
        return repository
            .findByChannelAndDeletedAtIsNullOrderByIsActiveDescCreatedAtDesc(channel)
            .map { it.toSummary() }
    }

    @Transactional(readOnly = true)
    fun get(id: Long): PromptDetail = load(id).toDetail()

    @Transactional
    fun create(req: PromptCreateRequest): PromptDetail {
        validateChannel(req.channel)
        PromptModels.validate(req.model)
        val version = req.version.trim()
        if (version.isBlank()) throw AppException(CommonErrorCode.VALIDATION)
        if (repository.findByChannelAndVersionAndDeletedAtIsNull(req.channel, version) != null) {
            throw AppException(AdminErrorCode.DUPLICATE_PROMPT_VERSION)
        }

        // clone(fromId) 본문으로 시작 → req에 명시된 조각만 덮어쓴다(삼중 elvis 회피로 복잡도 분산).
        val entity = AiPrompt(channel = req.channel, version = version, systemMd = "")
        req.fromId?.let { entity.copyBodyFrom(load(it)) }
        entity.applyCreateBody(req)
        entity.createdBy = currentAdminId()
        if (entity.systemMd.isBlank()) throw AppException(CommonErrorCode.VALIDATION)

        val saved = repository.save(entity)
        if (req.activate) activateInternal(saved)
        audit.record(
            action = "AI_PROMPT_CREATE",
            targetType = "ai_prompt",
            targetId = saved.id.toString(),
            summary = "프롬프트 ${req.channel}/$version 생성",
            metadata = mapOf("activate" to req.activate, "fromId" to req.fromId),
        )
        return load(saved.id!!).toDetail()
    }

    @Transactional
    fun update(
        id: Long,
        req: PromptUpdateRequest,
    ): PromptDetail {
        val target = load(id)
        applyVersionChange(target, req.version)
        applyBodyUpdates(target, req)
        repository.save(target)
        applyActiveTransition(target, req.isActive)
        // 본문/모델/활성 변경 모두 active 프롬프트 캐시에 영향 가능 → 무효화.
        promptResolver.invalidate(target.channel)
        audit.record(
            action = "AI_PROMPT_UPDATE",
            targetType = "ai_prompt",
            targetId = id.toString(),
            summary = "프롬프트 ${target.channel}/${target.version} 수정",
        )
        return target.toDetail()
    }

    /** 버전 변경(지정+비어있지않음+기존과 다름) 시 중복 검사 후 적용. */
    private fun applyVersionChange(
        target: AiPrompt,
        rawVersion: String?,
    ) {
        val newVersion = rawVersion?.trim()?.takeIf { it.isNotBlank() } ?: return
        if (newVersion == target.version) return
        if (repository.findByChannelAndVersionAndDeletedAtIsNull(target.channel, newVersion) != null) {
            throw AppException(AdminErrorCode.DUPLICATE_PROMPT_VERSION)
        }
        target.version = newVersion
    }

    /** 부분 수정: 지정된 본문/모델/파라미터 필드만 덮어쓴다(모델은 화이트리스트 검증). */
    private fun applyBodyUpdates(
        target: AiPrompt,
        req: PromptUpdateRequest,
    ) {
        req.systemMd?.let { target.systemMd = it }
        req.rulesMd?.let { target.rulesMd = it }
        req.outputSpecMd?.let { target.outputSpecMd = it }
        req.model?.let {
            PromptModels.validate(it)
            target.model = it
        }
        req.temperature?.let { target.temperature = it }
        req.maxTokens?.let { target.maxTokens = it }
        req.notes?.let { target.notes = it }
    }

    /** is_active 전환: true면 활성화 트랜잭션(다른 active 해제), false면 단순 비활성. */
    private fun applyActiveTransition(
        target: AiPrompt,
        isActive: Boolean?,
    ) {
        when (isActive) {
            true -> if (!target.isActive) activateInternal(target)
            false ->
                if (target.isActive) {
                    target.isActive = false
                    repository.save(target)
                }
            null -> {}
        }
    }

    @Transactional
    fun activate(id: Long): PromptDetail {
        val target = load(id)
        activateInternal(target)
        audit.record(
            action = "AI_PROMPT_ACTIVATE",
            targetType = "ai_prompt",
            targetId = id.toString(),
            summary = "프롬프트 ${target.channel}/${target.version} 활성화",
        )
        return target.toDetail()
    }

    @Transactional
    fun delete(id: Long) {
        val target = load(id)
        if (target.isActive) throw AppException(AdminErrorCode.CANNOT_DELETE_ACTIVE_PROMPT)
        target.deletedAt = Instant.now()
        repository.save(target)
        promptResolver.invalidate(target.channel)
        audit.record(
            action = "AI_PROMPT_DELETE",
            targetType = "ai_prompt",
            targetId = id.toString(),
            summary = "프롬프트 ${target.channel}/${target.version} 삭제",
        )
    }

    /** 같은 채널 기존 active를 먼저 해제(flush)한 뒤 대상을 활성화 — partial unique index 위반 방지. */
    private fun activateInternal(target: AiPrompt) {
        val others =
            repository
                .findByChannelAndDeletedAtIsNullOrderByIsActiveDescCreatedAtDesc(target.channel)
                .filter { it.isActive && it.id != target.id }
        if (others.isNotEmpty()) {
            others.forEach { it.isActive = false }
            repository.saveAll(others)
            repository.flush()
        }
        target.isActive = true
        repository.save(target)
        promptResolver.invalidate(target.channel)
    }

    private fun load(id: Long): AiPrompt = repository.findByIdAndDeletedAtIsNull(id) ?: throw AppException(AdminErrorCode.PROMPT_NOT_FOUND)

    private fun validateChannel(channel: String) {
        if (channel !in ALLOWED_CHANNELS) throw AppException(AdminErrorCode.INVALID_PROMPT_CHANNEL)
    }

    private fun currentAdminId(): String = TenantContext.currentUserId().toString()

    /** clone 원본의 본문 6조각을 복사한다(생성 시 기본값). */
    private fun AiPrompt.copyBodyFrom(source: AiPrompt) {
        systemMd = source.systemMd
        rulesMd = source.rulesMd
        outputSpecMd = source.outputSpecMd
        model = source.model
        temperature = source.temperature
        maxTokens = source.maxTokens
    }

    /** 생성 요청에서 지정된 본문/모델/파라미터 조각만 덮어쓴다. */
    private fun AiPrompt.applyCreateBody(req: PromptCreateRequest) {
        req.systemMd?.let { systemMd = it }
        req.rulesMd?.let { rulesMd = it }
        req.outputSpecMd?.let { outputSpecMd = it }
        req.model?.let { model = it }
        req.temperature?.let { temperature = it }
        req.maxTokens?.let { maxTokens = it }
        req.notes?.let { notes = it }
    }

    private fun AiPrompt.toSummary() =
        PromptSummary(
            id = id!!,
            channel = channel,
            version = version,
            isActive = isActive,
            model = model,
            temperature = temperature,
            maxTokens = maxTokens,
            notes = notes,
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun AiPrompt.toDetail() =
        PromptDetail(
            id = id!!,
            channel = channel,
            version = version,
            isActive = isActive,
            systemMd = systemMd,
            rulesMd = rulesMd,
            outputSpecMd = outputSpecMd,
            model = model,
            temperature = temperature,
            maxTokens = maxTokens,
            notes = notes,
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private companion object {
        val ALLOWED_CHANNELS = setOf("blog")
    }
}
