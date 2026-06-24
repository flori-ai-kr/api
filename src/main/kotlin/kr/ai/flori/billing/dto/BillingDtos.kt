package kr.ai.flori.billing.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import kr.ai.flori.billing.entity.BillingKey
import kr.ai.flori.billing.entity.PaymentHistory
import kr.ai.flori.billing.entity.Subscription
import java.time.Instant

data class PrepareResponse(
    val customerKey: String,
)

data class SubscribeRequest(
    @field:Pattern(regexp = "MONTHLY|YEARLY", message = "plan은 MONTHLY 또는 YEARLY")
    val plan: String,
    @field:NotBlank(message = "authKey는 필수입니다")
    val authKey: String,
    @field:NotBlank(message = "customerKey는 필수입니다")
    val customerKey: String,
)

data class CardSummary(
    val company: String?,
    val numberMasked: String?,
    val cardType: String?,
) {
    companion object {
        fun from(k: BillingKey?): CardSummary? = k?.let { CardSummary(it.cardCompany, it.cardNumberMasked, it.cardType) }
    }
}

data class SubscriptionResponse(
    val plan: String,
    val status: String,
    val currentPeriodEnd: Instant?,
    val nextBillingAt: Instant,
    val cancelAtPeriodEnd: Boolean,
    val card: CardSummary?,
) {
    companion object {
        fun of(
            sub: Subscription,
            card: BillingKey?,
        ): SubscriptionResponse =
            SubscriptionResponse(
                plan = sub.plan,
                status = sub.status,
                currentPeriodEnd = sub.currentPeriodEnd,
                nextBillingAt = sub.nextBillingAt,
                cancelAtPeriodEnd = sub.cancelAtPeriodEnd,
                card = CardSummary.from(card),
            )
    }
}

data class CardChangeRequest(
    @field:NotBlank val authKey: String,
    @field:NotBlank val customerKey: String,
)

data class PaymentSummary(
    val amount: Int,
    val status: String,
    val approvedAt: Instant?,
    val createdAt: Instant,
) {
    companion object {
        fun from(p: PaymentHistory): PaymentSummary = PaymentSummary(p.amount, p.status, p.approvedAt, p.createdAt)
    }
}

data class MeResponse(
    val subscription: SubscriptionResponse?,
    val recentPayments: List<PaymentSummary>,
)
