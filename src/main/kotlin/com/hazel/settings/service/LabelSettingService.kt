package com.hazel.settings.service

import com.hazel.common.error.AppException
import com.hazel.common.error.ErrorCode
import com.hazel.common.tenant.TenantContext
import com.hazel.settings.dto.LabelSettingResponse
import com.hazel.settings.entity.ExpenseCategory
import com.hazel.settings.entity.ExpensePaymentMethod
import com.hazel.settings.entity.LabelSetting
import com.hazel.settings.entity.SaleCategory
import com.hazel.settings.entity.SalePaymentMethod
import com.hazel.settings.repository.ExpenseCategoryRepository
import com.hazel.settings.repository.ExpensePaymentMethodRepository
import com.hazel.settings.repository.LabelSettingRepository
import com.hazel.settings.repository.SaleCategoryRepository
import com.hazel.settings.repository.SalePaymentMethodRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * value/label 설정 공통 CRUD. 모든 쿼리 TenantContext 격리(HARD).
 * value 미지정 시 label에서 영문 슬러그 생성. (value,user_id) 중복은 409.
 */
abstract class LabelSettingService<T : LabelSetting>(
    private val repository: LabelSettingRepository<T>,
) {
    protected abstract fun instantiate(userId: UUID): T

    protected abstract val defaultColor: String

    @Transactional(readOnly = true)
    open fun list(): List<LabelSettingResponse> =
        repository.findByUserIdOrderBySortOrderAsc(TenantContext.currentUserId()).map(::toResponse)

    @Transactional
    open fun add(
        label: String,
        color: String?,
        value: String?,
    ): LabelSettingResponse {
        val userId = TenantContext.currentUserId()
        val existing = repository.findByUserIdOrderBySortOrderAsc(userId)
        val entity = instantiate(userId)
        entity.value = value?.takeIf { it.isNotBlank() } ?: slugify(label)
        entity.label = label
        entity.color = color ?: defaultColor
        entity.sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: 0) + 1
        return toResponse(saveUnique(entity))
    }

    @Transactional
    open fun update(
        id: UUID,
        label: String,
        color: String,
    ): LabelSettingResponse {
        val entity = load(id)
        entity.label = label
        entity.color = color
        return toResponse(saveUnique(entity))
    }

    @Transactional
    open fun delete(id: UUID) {
        repository.delete(load(id))
    }

    private fun load(id: UUID): T =
        repository.findByIdAndUserId(id, TenantContext.currentUserId())
            ?: throw AppException(ErrorCode.NOT_FOUND, "설정을 찾을 수 없습니다")

    private fun saveUnique(entity: T): T =
        try {
            repository.saveAndFlush(entity)
        } catch (_: DataIntegrityViolationException) {
            throw AppException(ErrorCode.DUPLICATE, "이미 존재하는 항목입니다")
        }

    private fun toResponse(entity: T): LabelSettingResponse =
        LabelSettingResponse(requireNotNull(entity.id), entity.value, entity.label, entity.color, entity.sortOrder)

    private fun slugify(label: String): String =
        label
            .lowercase()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9_]"), "")
            .ifBlank { "item_${System.currentTimeMillis()}" }
}

@Service
class SaleCategorySettingService(
    repository: SaleCategoryRepository,
) : LabelSettingService<SaleCategory>(repository) {
    override fun instantiate(userId: UUID) = SaleCategory(userId)

    override val defaultColor = "#f43f5e"
}

@Service
class SalePaymentMethodSettingService(
    repository: SalePaymentMethodRepository,
) : LabelSettingService<SalePaymentMethod>(repository) {
    override fun instantiate(userId: UUID) = SalePaymentMethod(userId)

    override val defaultColor = "#3b82f6"
}

@Service
class ExpenseCategorySettingService(
    repository: ExpenseCategoryRepository,
) : LabelSettingService<ExpenseCategory>(repository) {
    override fun instantiate(userId: UUID) = ExpenseCategory(userId)

    override val defaultColor = "#6b7280"
}

@Service
class ExpensePaymentMethodSettingService(
    repository: ExpensePaymentMethodRepository,
) : LabelSettingService<ExpensePaymentMethod>(repository) {
    override fun instantiate(userId: UUID) = ExpensePaymentMethod(userId)

    override val defaultColor = "#3b82f6"
}
