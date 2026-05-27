package kr.ai.flori.sales.repository

import jakarta.persistence.criteria.Predicate
import kr.ai.flori.common.util.monthRange
import kr.ai.flori.sales.entity.Sale
import org.springframework.data.jpa.domain.Specification

/**
 * 매출 목록 동적 필터. 항상 user_id로 격리(멀티테넌시 HARD).
 * month: "YYYY"(연), "YYYY-MM"(월), "YYYY-MM-DD"(일) 형식 지원.
 */
object SaleSpecifications {
    fun filter(
        userId: Long,
        month: String?,
        categories: List<String>?,
        payments: List<String>?,
        channels: List<String>?,
        search: String?,
    ): Specification<Sale> =
        Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            predicates += cb.equal(root.get<Long>("userId"), userId)

            monthRange(month)?.let { (start, end) ->
                predicates += cb.greaterThanOrEqualTo(root.get("date"), start)
                predicates += cb.lessThanOrEqualTo(root.get("date"), end)
            }
            if (!categories.isNullOrEmpty()) {
                predicates += root.get<String>("productCategory").`in`(categories)
            }
            if (!payments.isNullOrEmpty()) {
                predicates += root.get<String>("paymentMethod").`in`(payments)
            }
            if (!channels.isNullOrEmpty()) {
                predicates += root.get<String>("reservationChannel").`in`(channels)
            }
            if (!search.isNullOrBlank()) {
                val pattern = "%${search.lowercase().replace("%", "\\%").replace("_", "\\_")}%"
                predicates +=
                    cb.or(
                        cb.like(cb.lower(root.get("productCategory")), pattern),
                        cb.like(cb.lower(root.get("productName")), pattern),
                        cb.like(cb.lower(root.get("customerName")), pattern),
                    )
            }
            cb.and(*predicates.toTypedArray())
        }
}
