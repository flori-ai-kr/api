package kr.ai.flori.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import java.time.Instant

/**
 * refresh 토큰. 원문이 아닌 SHA-256 해시만 저장한다(원문 토큰 DB 저장 금지 — HARD).
 *
 * 회전/세션 추적 + 통계용 메타데이터를 함께 보관한다(유저 데이터 축적 → 세션 분석):
 * - [status]: 토큰 수명 상태(ACTIVE→ROTATED/LOGGED_OUT/EXPIRED). 종료 사유별 통계.
 * - [parentTokenId]/[sessionStartedAt]/[reissuedCount]: 회전 계보 — 세션 lifetime·활성도 통계.
 * - [clientId]/[deviceId]/[userAgent]/[createdIp]: 발급 시 요청 컨텍스트 — 채널·기기·지역 통계.
 *   (ClientContext가 캡처. 필터를 거치지 않는 직접 호출 등에선 비어 있을 수 있어 모두 nullable.)
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "token_hash", nullable = false, unique = true)
    var tokenHash: String,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
    // 세션 최초 시작 시각. 회전 시 부모의 값을 계승해 한 세션의 계보 기준점이 된다.
    @Column(name = "session_started_at", nullable = false)
    var sessionStartedAt: Instant = Instant.now(),
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "status", nullable = false)
    var status: String = RefreshTokenStatuses.ACTIVE

    @Column(name = "parent_token_id")
    var parentTokenId: Long? = null

    @Column(name = "client_id")
    var clientId: String? = null

    @Column(name = "device_id")
    var deviceId: String? = null

    @Column(name = "user_agent")
    var userAgent: String? = null

    @Column(name = "created_ip")
    var createdIp: String? = null

    @Column(name = "reissued_count", nullable = false)
    var reissuedCount: Int = 0

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null
}
