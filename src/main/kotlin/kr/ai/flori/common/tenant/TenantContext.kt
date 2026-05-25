package kr.ai.flori.common.tenant

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import java.util.UUID

/**
 * 요청 스코프 테넌트(현재 사용자) 컨텍스트.
 *
 * 멀티테넌시 = 보안 1순위(HARD): 모든 데이터 쿼리는 여기서 얻은 userId로 격리한다.
 * JWT 인증 필터가 요청 시작 시 set, 요청 종료 시 clear 한다.
 */
object TenantContext {
    private val holder = ThreadLocal<UUID?>()

    fun set(userId: UUID) = holder.set(userId)

    fun clear() = holder.remove()

    /** 인증된 사용자 id. 없으면 인증 누락으로 간주(보안상 안전한 기본값). */
    fun currentUserId(): UUID = holder.get() ?: throw AppException(ErrorCode.UNAUTHORIZED)

    fun currentUserIdOrNull(): UUID? = holder.get()
}
