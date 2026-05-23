package com.hazel.settings.repository

import com.hazel.settings.entity.ExpenseCategory
import com.hazel.settings.entity.ExpensePaymentMethod
import com.hazel.settings.entity.LabelSetting
import com.hazel.settings.entity.SaleCategory
import com.hazel.settings.entity.SalePaymentMethod
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
