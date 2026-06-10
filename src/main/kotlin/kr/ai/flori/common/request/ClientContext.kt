package kr.ai.flori.common.request

/**
 * 요청 스코프 클라이언트 컨텍스트(발급 메타데이터 통계 수집용).
 *
 * [ClientContextFilter]가 요청 시작 시 헤더/원격주소에서 채우고 종료 시 clear 한다.
 * refresh 토큰 발급(AuthService.issueTokens)이 여기서 값을 읽어 저장한다.
 * 필터를 거치지 않는 경로(테스트의 서비스 직접 호출 등)에선 비어 있을 수 있어 모든 값 nullable.
 *
 * [TenantContext][kr.ai.flori.common.tenant.TenantContext]와 동일한 ThreadLocal 패턴.
 */
object ClientContext {
    data class Info(
        val clientId: String?,
        val deviceId: String?,
        val userAgent: String?,
        val ip: String?,
    )

    private val holder = ThreadLocal<Info?>()

    fun set(info: Info) = holder.set(info)

    fun clear() = holder.remove()

    /** 현재 요청의 클라이언트 컨텍스트. 필터를 거치지 않았으면 null. */
    fun current(): Info? = holder.get()
}
