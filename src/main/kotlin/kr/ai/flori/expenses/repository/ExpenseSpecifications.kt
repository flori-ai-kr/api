package kr.ai.flori.expenses.repository

import jakarta.persistence.criteria.Predicate
import kr.ai.flori.common.util.monthRange
import kr.ai.flori.expenses.entity.Expense
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate

/**
 * 지출 목록 동적 필터. 항상 user_id로 격리(멀티테넌시 HARD).
 * month: "YYYY"(연), "YYYY-MM"(월), "YYYY-MM-DD"(일) 형식 지원.
 * startDate/endDate: 날짜 범위 직접 지정 (month보다 우선).
 * 검색: 물품명·거래처·메모 대상.
 */
object ExpenseSpecifications {
    @Suppress("LongParameterList")
    fun filter(
        userId: Long,
        month: String?,
        startDate: String? = null,
        endDate: String? = null,
        categories: List<Long>?,
        payments: List<Long>?,
        search: String?,
    ): Specification<Expense> =
        Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            predicates += cb.equal(root.get<Long>("userId"), userId)

            if (!startDate.isNullOrBlank() && !endDate.isNullOrBlank()) {
                predicates += cb.greaterThanOrEqualTo(root.get("date"), LocalDate.parse(startDate))
                predicates += cb.lessThanOrEqualTo(root.get("date"), LocalDate.parse(endDate))
            } else {
                monthRange(month)?.let { (start, end) ->
                    predicates += cb.greaterThanOrEqualTo(root.get("date"), start)
                    predicates += cb.lessThanOrEqualTo(root.get("date"), end)
                }
            }
            if (!categories.isNullOrEmpty()) {
                predicates += root.get<Long>("categoryId").`in`(categories)
            }
            if (!payments.isNullOrEmpty()) {
                predicates += root.get<Long>("paymentMethodId").`in`(payments)
            }
            if (!search.isNullOrBlank()) {
                val pattern = "%${search.lowercase().replace("%", "\\%").replace("_", "\\_")}%"
                // ESCAPE 미지정 시 Hibernate 6가 이스케이프를 무효화해 summary(JDBC)와 결과가 갈린다 — 명시 필수.
                predicates +=
                    cb.or(
                        cb.like(cb.lower(root.get("itemName")), pattern, ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("vendor")), pattern, ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("memo")), pattern, ESCAPE_CHAR),
                    )
            }
            cb.and(*predicates.toTypedArray())
        }

    private const val ESCAPE_CHAR = '\\'
}
