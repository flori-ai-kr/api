package kr.ai.flori.interview.repository

import kr.ai.flori.interview.entity.InterviewRequest
import org.springframework.data.jpa.repository.JpaRepository

interface InterviewRequestRepository : JpaRepository<InterviewRequest, Long> {
    fun existsByPhone(phone: String): Boolean
}
