package kr.ai.flori.settings.repository

import kr.ai.flori.settings.entity.ExpenseCategory
import kr.ai.flori.settings.entity.ExpensePaymentMethod
import kr.ai.flori.settings.entity.LabelSetting
import kr.ai.flori.settings.entity.SaleCategory
import kr.ai.flori.settings.entity.SalePaymentMethod
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.NoRepositoryBean
import java.util.UUID

@NoRepositoryBean
interface LabelSettingRepository<T : LabelSetting> : JpaRepository<T, UUID> {
    fun findByUserIdOrderBySortOrderAsc(userId: UUID): List<T>

    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): T?
}

interface SaleCategoryRepository : LabelSettingRepository<SaleCategory>

interface SalePaymentMethodRepository : LabelSettingRepository<SalePaymentMethod>

interface ExpenseCategoryRepository : LabelSettingRepository<ExpenseCategory>

interface ExpensePaymentMethodRepository : LabelSettingRepository<ExpensePaymentMethod>
