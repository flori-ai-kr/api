package kr.ai.flori.waitlist.repository

import kr.ai.flori.waitlist.entity.WaitlistRegistration
import org.springframework.data.jpa.repository.JpaRepository

interface WaitlistRegistrationRepository : JpaRepository<WaitlistRegistration, Long> {
    fun existsByPhone(phone: String): Boolean
}
