package kr.ai.flori.waitlist.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.waitlist.dto.WaitlistCountResponse
import kr.ai.flori.waitlist.dto.WaitlistRegisterRequest
import kr.ai.flori.waitlist.dto.WaitlistRegisterResponse
import kr.ai.flori.waitlist.entity.WaitlistRegistration
import kr.ai.flori.waitlist.error.WaitlistErrorCode
import kr.ai.flori.waitlist.repository.WaitlistRegistrationRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사전등록 서비스. 인증/테넌시 없음(공개 모집).
 * 선착순 [CAPACITY]명까지. email은 trim+소문자로 정규화·중복검사. email·shopName 모두 필수.
 */
@Service
class WaitlistService(
    private val repository: WaitlistRegistrationRepository,
) {
    // 낙관적 저장: 마감 가드 + 사전 중복검사 + 저장 시 UNIQUE 경쟁 캐치로 throw가 3개 — 의도된 패턴
    @Suppress("ThrowsCount")
    @Transactional
    fun register(request: WaitlistRegisterRequest): WaitlistRegisterResponse {
        if (repository.count() >= CAPACITY) {
            throw AppException(WaitlistErrorCode.CLOSED)
        }
        val email = normalizeEmail(request.email)
        if (repository.existsByEmail(email)) {
            throw AppException(WaitlistErrorCode.ALREADY_REGISTERED)
        }
        try {
            repository.save(
                WaitlistRegistration(
                    email = email,
                    shopName = request.shopName.trim(),
                ),
            )
        } catch (
            @Suppress("SwallowedException") e: DataIntegrityViolationException,
        ) {
            // cause를 전달하지 않는다: JDBC 제약위반 메시지("Key (email)=(...) already exists")가
            // 글로벌 핸들러 로그에 이메일(PII)을 남기는 것을 막는다. 동시 등록 경쟁은 멱등 409로 변환.
            throw AppException(WaitlistErrorCode.ALREADY_REGISTERED)
        }
        val count = repository.count()
        return WaitlistRegisterResponse(count = count, capacity = CAPACITY, closed = count >= CAPACITY)
    }

    @Transactional(readOnly = true)
    fun count(): WaitlistCountResponse {
        val count = repository.count()
        return WaitlistCountResponse(count = count, capacity = CAPACITY, closed = count >= CAPACITY)
    }

    private fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    companion object {
        const val CAPACITY = 100
    }
}
