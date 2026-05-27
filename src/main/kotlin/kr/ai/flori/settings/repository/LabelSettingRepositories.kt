package kr.ai.flori.settings.repository

import kr.ai.flori.settings.entity.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.NoRepositoryBean

@NoRepositoryBean
interface LabelSettingRepository<T : LabelSetting> : JpaRepository<T, Long> {
    fun findByUserIdOrderBySortOrderAsc(userId: Long): List<T>

    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): T?
}

interface SaleCategoryRepository : LabelSettingRepository<SaleCategory>

interface SalePaymentMethodRepository : LabelSettingRepository<SalePaymentMethod>

interface ExpenseCategoryRepository : LabelSettingRepository<ExpenseCategory>

interface ExpensePaymentMethodRepository : LabelSettingRepository<ExpensePaymentMethod>
