package kr.ai.flori.settings.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.settings.dto.LabelSettingResponse
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.entity.LabelSetting
import kr.ai.flori.settings.repository.LabelSettingRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * value/label 설정 공통 CRUD. (domain, kind)로 용도를 고정한 단일 테이블(label_settings) 위에서 동작한다.
 * 모든 쿼리 TenantContext 격리(HARD). value 미지정 시 label에서 영문 슬러그 생성.
 * (user_id, domain, kind, value) 중복은 409.
 */
abstract class LabelSettingService(
    private val repository: LabelSettingRepository,
    private val domain: String,
    private val kind: String,
) {
    @Transactional(readOnly = true)
    open fun list(): List<LabelSettingResponse> =
        repository
            .findByUserIdAndDomainAndKindOrderBySortOrderAsc(TenantContext.currentUserId(), domain, kind)
            .map(::toResponse)

    @Transactional
    open fun add(
        label: String,
        value: String?,
    ): LabelSettingResponse {
        val userId = TenantContext.currentUserId()
        val existing = repository.findByUserIdAndDomainAndKindOrderBySortOrderAsc(userId, domain, kind)
        val entity = LabelSetting(userId, domain, kind)
        entity.value = value?.takeIf { it.isNotBlank() } ?: slugify(label)
        entity.label = label
        entity.sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: 0) + 1
        return toResponse(saveUnique(entity))
    }

    @Transactional
    open fun update(
        id: Long,
        label: String,
    ): LabelSettingResponse {
        val entity = load(id)
        entity.label = label
        return toResponse(saveUnique(entity))
    }

    @Transactional
    open fun delete(id: Long) {
        repository.delete(load(id))
    }

    /**
     * 순서 변경. orderedIds 는 현재 (user,domain,kind) 전체 항목 id 와 정확히 일치해야 한다(부분/외부 id 거부).
     * 나열 순서대로 sort_order 를 1..N 으로 재부여한다.
     */
    @Transactional
    open fun reorder(orderedIds: List<Long>): List<LabelSettingResponse> {
        val userId = TenantContext.currentUserId()
        val items = repository.findByUserIdAndDomainAndKindOrderBySortOrderAsc(userId, domain, kind)
        val byId = items.associateBy { requireNotNull(it.id) }
        if (orderedIds.size != items.size || orderedIds.toSet() != byId.keys) {
            throw AppException(CommonErrorCode.VALIDATION, "순서 목록이 현재 항목과 일치하지 않습니다")
        }
        orderedIds.forEachIndexed { idx, id -> byId.getValue(id).sortOrder = idx + 1 }
        return repository.saveAll(items).sortedBy { it.sortOrder }.map(::toResponse)
    }

    private fun load(id: Long): LabelSetting =
        repository.findByIdAndUserIdAndDomainAndKind(id, TenantContext.currentUserId(), domain, kind)
            ?: throw AppException(CommonErrorCode.NOT_FOUND, "설정을 찾을 수 없습니다")

    private fun saveUnique(entity: LabelSetting): LabelSetting =
        try {
            repository.saveAndFlush(entity)
        } catch (_: DataIntegrityViolationException) {
            throw AppException(CommonErrorCode.CONFLICT, "이미 존재하는 항목입니다")
        }

    private fun toResponse(entity: LabelSetting): LabelSettingResponse =
        LabelSettingResponse(requireNotNull(entity.id), entity.value, entity.label, entity.sortOrder)

    private fun slugify(label: String): String =
        label
            .lowercase()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9_]"), "")
            .ifBlank { "item_${System.currentTimeMillis()}" }
}

@Service
class SaleCategorySettingService(
    repository: LabelSettingRepository,
) : LabelSettingService(repository, LabelDomains.SALE, LabelKinds.CATEGORY)

@Service
class SalePaymentMethodSettingService(
    repository: LabelSettingRepository,
) : LabelSettingService(repository, LabelDomains.SALE, LabelKinds.PAYMENT)

@Service
class SaleChannelSettingService(
    repository: LabelSettingRepository,
) : LabelSettingService(repository, LabelDomains.SALE, LabelKinds.CHANNEL)

@Service
class ExpenseCategorySettingService(
    repository: LabelSettingRepository,
) : LabelSettingService(repository, LabelDomains.EXPENSE, LabelKinds.CATEGORY)

@Service
class ExpensePaymentMethodSettingService(
    repository: LabelSettingRepository,
) : LabelSettingService(repository, LabelDomains.EXPENSE, LabelKinds.PAYMENT)
