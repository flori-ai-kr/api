package kr.ai.flori.support

import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.user.repository.UserRepository
import java.util.UUID

/**
 * 테스트용 테넌트 부트스트랩. 22개 테스트 파일에 복붙되어 있던 newTenant() 패턴의 SSOT.
 * 실제 가입 경로(TestAccounts.register)로 User+시드를 만들고 TenantContext까지 설정한다.
 * 사용한 테스트는 @AfterEach 에서 TenantContext.clear() 를 호출해야 한다.
 */
object TestTenants {
    fun bootstrap(
        authService: AuthService,
        tokenProvider: JwtTokenProvider,
        userRepository: UserRepository,
        email: String = "tenant-${UUID.randomUUID()}@flori.dev",
    ): Long {
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(requireNotNull(userRepository.findByEmail(email)).id)
        TenantContext.set(userId)
        return userId
    }

    /** 특정 테넌트 컨텍스트로 블록을 실행하고 반드시 원복한다(테넌트 격리 테스트용). */
    fun <T> runAs(
        userId: Long,
        block: () -> T,
    ): T {
        val previous = TenantContext.currentUserIdOrNull()
        TenantContext.set(userId)
        return try {
            block()
        } finally {
            if (previous != null) TenantContext.set(previous) else TenantContext.clear()
        }
    }
}
