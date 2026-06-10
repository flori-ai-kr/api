package kr.ai.flori.interview.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.interview.dto.InterviewApplyRequest
import kr.ai.flori.interview.dto.InterviewApplyResponse
import kr.ai.flori.interview.entity.InterviewRequest
import kr.ai.flori.interview.error.InterviewErrorCode
import kr.ai.flori.interview.event.InterviewRequestedEvent
import kr.ai.flori.interview.repository.InterviewRequestRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 유저 인터뷰 모집 서비스. 인증/테넌시 없음(공개 모집).
 * phone은 숫자만 남겨 정규화·중복검사. name·phone 모두 필수.
 * 저장 커밋 후 [InterviewRequestedEvent]를 발행해 Discord 알림을 비동기로 보낸다.
 */
@Service
class InterviewService(
    private val repository: InterviewRequestRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun apply(request: InterviewApplyRequest): InterviewApplyResponse {
        val name = request.name.trim()
        val phone = normalizePhone(request.phone)
        if (repository.existsByPhone(phone)) {
            throw AppException(InterviewErrorCode.ALREADY_APPLIED)
        }
        try {
            repository.save(InterviewRequest(name = name, phone = phone))
        } catch (
            @Suppress("SwallowedException") e: DataIntegrityViolationException,
        ) {
            // cause 미전달: JDBC 제약위반 메시지가 글로벌 핸들러 로그에 전화번호(PII)를 남기는 것을 막는다.
            // 동시 신청 경쟁은 멱등 409로 변환.
            throw AppException(InterviewErrorCode.ALREADY_APPLIED)
        }
        eventPublisher.publishEvent(InterviewRequestedEvent(name = name, phone = phone))
        return InterviewApplyResponse(applied = true)
    }

    // 표기 차이(하이픈 유무 등)에 무관한 중복 판정을 위해 숫자만 남긴다.
    private fun normalizePhone(raw: String): String = raw.filter { it.isDigit() }
}
