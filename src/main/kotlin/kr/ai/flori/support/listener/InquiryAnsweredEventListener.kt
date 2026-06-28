package kr.ai.flori.support.listener

import kr.ai.flori.common.push.PushDispatcher
import kr.ai.flori.common.push.PushLinks
import kr.ai.flori.common.push.PushTypes
import kr.ai.flori.support.event.InquiryAnsweredEvent
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class InquiryAnsweredEventListener(
    private val pushDispatcher: PushDispatcher,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: InquiryAnsweredEvent) {
        pushDispatcher.sendToUser(
            userId = event.userId,
            title = "문의에 답변이 도착했어요",
            body = "\"${event.title}\"에 대한 답변을 확인하세요.",
            link = PushLinks.support(event.inquiryId),
            type = PushTypes.INQUIRY_ANSWERED,
        )
    }
}
