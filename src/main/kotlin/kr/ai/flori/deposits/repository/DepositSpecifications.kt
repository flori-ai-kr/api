package kr.ai.flori.deposits.repository

import jakarta.persistence.criteria.Predicate
import kr.ai.flori.sales.entity.Sale
import org.springframework.data.jpa.domain.Specification
import java.time.YearMonth
import java.util.UUID

/**
 * 입금대조 목록 필터(카드 매출 한정). 항상 user_id 격리(멀티테넌시 HARD).
 */
object DepositSpecifications {
    fun filter(
        userId: UUID,
        month: String?,
        status: String?,
        cardCompany: String?,
    ): Specification<Sale> =
        Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            predicates += cb.equal(root.get<UUID>("userId"), userId)
            predicates += cb.equal(root.get<String>("paymentMethod"), PAYMENT_CARD)

            if (!month.isNullOrBlank()) {
                val ym = YearMonth.parse(month)
                predicates += cb.greaterThanOrEqualTo(root.get("date"), ym.atDay(1))
                predicates += cb.lessThanOrEqualTo(root.get("date"), ym.atEndOfMonth())
            }
            if (!status.isNullOrBlank() && status != STATUS_ALL) {
                predicates += cb.equal(root.get<String>("depositStatus"), status)
            }
            if (!cardCompany.isNullOrBlank() && cardCompany != STATUS_ALL) {
                predicates += cb.equal(root.get<String>("cardCompany"), cardCompany)
            }
            cb.and(*predicates.toTypedArray())
        }

    private const val PAYMENT_CARD = "card"
    private const val STATUS_ALL = "all"
}
