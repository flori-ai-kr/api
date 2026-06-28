package kr.ai.flori.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import java.time.Instant

/**
 * 가입 시점 약관 동의 기록(users와 1:1). PK이자 신원인 user_id를 공유한다(임의 user_id 주입 불가 — 본질적 격리).
 *
 * 분쟁 시 "동의받은 사실"의 입증 근거 — 동의 항목·시각·약관버전을 보존한다.
 * 필수(이용약관·개인정보 수집·이용)는 미동의면 가입이 성립하지 않으므로(컨트롤러 `@AssertTrue`) 항상 true로 저장된다.
 * marketing 은 선택 — 정보통신망법 제50조 광고성 정보 수신 동의/철회 관리의 기준이 된다.
 */
@Entity
@Table(name = "user_consents")
class UserConsent(
    @Id
    @Column(name = "user_id")
    var userId: Long,
    @Column(name = "terms_agreed", nullable = false)
    var termsAgreed: Boolean,
    @Column(name = "privacy_agreed", nullable = false)
    var privacyAgreed: Boolean,
    @Column(name = "marketing_agreed", nullable = false)
    var marketingAgreed: Boolean,
    @Column(name = "policy_version", nullable = false)
    var policyVersion: String,
    // 동의 시각(법적 "동의받은 시점"). 가입 시 명시적으로 기록한다(auditing created_at과 의미 분리).
    @Column(name = "agreed_at", nullable = false)
    var agreedAt: Instant,
) : BaseEntity()
