package kr.ai.flori.auth.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kr.ai.flori.auth.dto.OAuthResult
import kr.ai.flori.auth.dto.RegisterCompleteRequest
import kr.ai.flori.auth.dto.TokenResponse
import kr.ai.flori.auth.entity.RefreshToken
import kr.ai.flori.auth.entity.RefreshTokenStatuses
import kr.ai.flori.auth.error.AuthErrorCode
import kr.ai.flori.auth.event.UserRegisteredEvent
import kr.ai.flori.auth.oauth.AccessTokenOAuthClient
import kr.ai.flori.auth.oauth.SocialOAuthClient
import kr.ai.flori.auth.oauth.SocialUserInfo
import kr.ai.flori.auth.repository.RefreshTokenRepository
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.request.ClientContext
import kr.ai.flori.common.security.JwtProperties
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.user.dto.UserResponse
import kr.ai.flori.user.entity.User
import kr.ai.flori.user.entity.UserProfile
import kr.ai.flori.user.repository.UserProfileRepository
import kr.ai.flori.user.repository.UserRepository
import kr.ai.flori.user.service.toResponse
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * 인증 서비스(소셜 전용): 소셜 로그인 · 가입 완료(register/complete) · refresh 회전 · 로그아웃.
 *
 * - 비밀번호는 존재하지 않는다(이메일/비밀번호 가입 폐지). 신원은 (provider, providerId).
 * - User 행은 register/complete 시점에만 생성된다 — 온보딩 중도 이탈 시 유령 계정이 남지 않는다.
 * - access는 자체 JWT(짧은 TTL), refresh는 불투명 난수 + DB에 SHA-256 해시 저장.
 * - refresh 회전: 사용 시 기존 토큰 무효화 후 새 토큰 발급.
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val tokenProvider: JwtTokenProvider,
    private val jwtProperties: JwtProperties,
    private val seeder: DefaultDataSeeder,
    private val userProfileRepository: UserProfileRepository,
    // 제공자 일반화: 빈 이름(KAKAO/GOOGLE/NAVER)을 키로 소셜 클라이언트 주입. 새 제공자는 빈 추가만으로 확장.
    private val socialClients: Map<String, SocialOAuthClient>,
    private val eventPublisher: ApplicationEventPublisher,
    transactionManager: PlatformTransactionManager,
) {
    private val secureRandom = SecureRandom()

    // refresh 회전을 캐시 로더 안에서 독립 트랜잭션으로 실행하기 위한 템플릿(self-invocation @Transactional 우회).
    private val txTemplate = TransactionTemplate(transactionManager)

    // @MX:WARN: [AUTO] refresh 멱등 캐시 — 같은 raw refresh 토큰 호출은 단 1회만 회전한다.
    // @MX:REASON: refresh 토큰은 단일 사용 회전이라, 동시/중복 호출(프리페치·멀티탭·쿠키저장 실패 재시도)이
    //   각자 회전하면 첫 호출만 성공하고 나머지는 INVALID_TOKEN → 로그아웃. 발급 결과를 hash(raw)로 짧게
    //   캐시해 동시 호출에 동일 토큰을 돌려준다. get(key, loader)는 키당 1회만 로더를 원자적으로 실행한다.
    // null이면 멱등 비활성(ttl<=0) — refresh가 매번 rotate를 직접 호출(strict 회전).
    // [멀티 인스턴스 제약] 이 캐시는 프로세스 로컬이다. API 인스턴스가 2개 이상이고 동시 두 요청이
    //   서로 다른 인스턴스로 라우팅되면 dedup이 적용되지 않아 한쪽은 INVALID_TOKEN을 받을 수 있다.
    //   완전한 멱등 보장이 필요하면 Redis 등 분산 캐시로 교체해야 한다.
    private val refreshDedup: Cache<String, TokenResponse>? =
        if (jwtProperties.refreshDedupTtlSeconds > 0) {
            Caffeine
                .newBuilder()
                .expireAfterWrite(Duration.ofSeconds(jwtProperties.refreshDedupTtlSeconds))
                .maximumSize(REFRESH_DEDUP_MAX_SIZE)
                .build()
        } else {
            null
        }

    /**
     * 소셜 로그인 일반화 진입점. provider(KAKAO/GOOGLE/NAVER)로 클라이언트를 선택해 인증코드를 검증한다.
     * - 기존 신원 → 로그인 토큰 발급(registered=true).
     * - 신규 신원 → User를 만들지 않고 registerToken + 소셜 기본값 반환(registered=false).
     * 네이버는 state가 필수다.
     *
     * @MX:ANCHOR: [AUTO] 모든 소셜 OAuth 엔드포인트(kakao/google/naver)가 의존하는 단일 진입점.
     * @MX:REASON: 컨트롤러 3개 경로 + register/complete 신원 계약의 출처(fan_in>=3). 반환 계약 변경은 웹 세션에 파급된다.
     *
     * @Transactional 미적용: 외부 OAuth HTTP 호출(client.authenticate)이 DB 커넥션을 점유하지 않도록.
     * DB 작업(find-or-create·토큰 발급)은 [loginOrRegister]로 분리됐고, 그 근거는 해당 메서드 주석 참조.
     */
    fun oauthLogin(
        provider: String,
        code: String,
        redirectUri: String,
        state: String?,
    ): OAuthResult {
        val client =
            socialClients[provider]
                ?: throw AppException(CommonErrorCode.VALIDATION, "지원하지 않는 소셜 제공자입니다: $provider")
        val info = client.authenticate(code, redirectUri, state)
        return loginOrRegister(info)
    }

    /**
     * 앱 네이티브 SDK access token 경로. 카카오처럼 커스텀 스킴 리다이렉트가 막혀 웹 code 플로우를 못 쓰는
     * 제공자용. code 교환 없이 access token으로 신원을 검증한 뒤 [loginOrRegister]로 합류한다.
     * → 웹(code)·앱(accessToken) 모두 동일한 find-or-create·토큰·OAuthResult 경로를 공유한다.
     */
    fun oauthLoginWithAccessToken(
        provider: String,
        accessToken: String,
    ): OAuthResult {
        val client =
            socialClients[provider]
                ?: throw AppException(CommonErrorCode.VALIDATION, "지원하지 않는 소셜 제공자입니다: $provider")
        if (client !is AccessTokenOAuthClient) {
            throw AppException(
                CommonErrorCode.VALIDATION,
                "토큰 기반 로그인을 지원하지 않는 제공자입니다: $provider",
            )
        }
        val info = client.authenticateWithAccessToken(accessToken)
        return loginOrRegister(info)
    }

    /**
     * 소셜 신원 → 기존 사용자면 로그인 토큰(registered=true), 신규면 registerToken(registered=false). 웹(code)·앱(accessToken) 공통 합류점.
     * @Transactional 미적용: DB 작업이 단건(findBy 조회, issueTokens의 refresh save)이라 각자 자체 트랜잭션으로 충분하다.
     */
    private fun loginOrRegister(info: SocialUserInfo): OAuthResult {
        val existing = userRepository.findByProviderAndProviderId(info.provider, info.providerId)
        if (existing != null) {
            if (!existing.isActive) {
                throw AppException(CommonErrorCode.FORBIDDEN, "비활성화된 계정입니다")
            }
            return OAuthResult(registered = true, token = issueTokens(existing))
        }

        // 신규 신원 → User 미생성. registerToken으로 가입 대기 상태만 전달(온보딩 완료 시 생성).
        val registerToken =
            tokenProvider.generateRegisterToken(
                provider = info.provider,
                providerId = info.providerId,
                email = info.email,
                nickname = info.nickname,
            )
        return OAuthResult(
            registered = false,
            registerToken = registerToken,
            socialEmail = info.email,
            socialNickname = info.nickname,
        )
    }

    /**
     * 가입 완료(= 온보딩). registerToken을 자격증명으로 검증하고 User + 가게 프로필 + 기본 설정 시드를
     * 한 트랜잭션에서 생성한 뒤 로그인 토큰을 발급한다.
     *
     * 멀티테넌시: 신원은 절대 요청 본문이 아닌 registerToken에서만 도출한다(provider/providerId).
     * 충돌 정책(자동 병합 금지): 같은 신원이 이미 가입 → DUPLICATE, 이메일이 타 계정 사용 중 → DUPLICATE.
     */
    @Transactional
    fun registerComplete(request: RegisterCompleteRequest): TokenResponse {
        val principal =
            tokenProvider.parseRegisterToken(request.registerToken)
                ?: throw AppException(CommonErrorCode.INVALID_TOKEN, "가입 토큰이 유효하지 않거나 만료되었습니다")
        verifyRegisterable(principal.provider, principal.providerId, request.email, request.nickname)

        val user =
            try {
                // saveAndFlush: 시더의 raw JDBC INSERT가 같은 트랜잭션에서 user FK를 참조하므로 즉시 flush
                userRepository.saveAndFlush(
                    User(
                        email = request.email,
                        nickname = request.nickname,
                        provider = principal.provider,
                        providerId = principal.providerId,
                    ),
                )
            } catch (e: DataIntegrityViolationException) {
                // 동시 가입 경쟁(unique 충돌): pre-check를 통과했어도 flush 시점에 unique 인덱스가 거부할 수 있다.
                // 충돌한 제약을 식별해 신원/이메일/닉네임 중 알맞은 메시지로 멱등 변환한다(at-most-once 보장).
                throw duplicateFromConstraint(e)
            }
        val userId = requireNotNull(user.id)

        userProfileRepository.save(
            UserProfile(
                userId = userId,
                storeName = request.storeName,
                phoneNumber = request.phoneNumber,
                regionSido = request.regionSido,
            ).apply {
                ownerName = request.ownerName
                regionSigungu = request.regionSigungu
                ownerAgeRange = request.ownerAgeRange
                interests = request.interests?.toTypedArray() ?: emptyArray()
                specialties = request.specialties?.toTypedArray() ?: emptyArray()
                referralSources = request.referralSources.toTypedArray()
            },
        )
        seeder.seedForNewUser(userId)
        eventPublisher.publishEvent(
            UserRegisteredEvent(
                userId = userId,
                nickname = user.nickname,
                provider = user.provider,
            ),
        )
        return issueTokens(user)
    }

    /**
     * 가입 가능 여부 검증(자동 병합 금지 정책). 충돌 시 신원/이메일/닉네임을 구분한 DUPLICATE 메시지로 던진다.
     * - 같은 (provider, providerId)가 이미 가입 → registerToken 재사용 차단.
     * - 이메일이 타 계정 사용 중 → 이메일 선점 공격 시 다른 계정에 붙이지 않는다.
     * - 닉네임이 타 계정 사용 중 → 전역 유일(uq_users_nickname) 위반 선검사.
     */
    private fun verifyRegisterable(
        provider: String,
        providerId: String,
        email: String,
        nickname: String,
    ) {
        val conflict: AppException? =
            when {
                userRepository.findByProviderAndProviderId(provider, providerId) != null ->
                    AppException(AuthErrorCode.ALREADY_REGISTERED, DUP_IDENTITY)
                userRepository.existsByEmail(email) ->
                    AppException(AuthErrorCode.DUPLICATE_EMAIL, DUP_EMAIL)
                userRepository.existsByNickname(nickname) ->
                    AppException(AuthErrorCode.DUPLICATE_NICKNAME, DUP_NICKNAME)
                else -> null
            }
        if (conflict != null) {
            throw conflict
        }
    }

    /**
     * DataIntegrityViolationException(unique 충돌)을 충돌 제약명으로 구분해 알맞은 AuthErrorCode로 변환한다.
     * pre-check가 세 종류를 모두 선검사하므로 이 경로는 동시성 경쟁 fallback이며, 제약명 매칭은 best-effort다.
     */
    private fun duplicateFromConstraint(e: DataIntegrityViolationException): AppException {
        val detail = (e.mostSpecificCause.message ?: e.message ?: "").lowercase()
        return when {
            detail.contains("uq_users_nickname") -> AppException(AuthErrorCode.DUPLICATE_NICKNAME, DUP_NICKNAME)
            detail.contains("uq_users_provider_identity") -> AppException(AuthErrorCode.ALREADY_REGISTERED, DUP_IDENTITY)
            detail.contains("email") -> AppException(AuthErrorCode.DUPLICATE_EMAIL, DUP_EMAIL)
            else -> AppException(CommonErrorCode.CONFLICT, "중복 충돌이 발생했습니다. 다시 시도해 주세요")
        }
    }

    /** 닉네임 사용 가능 여부 사전 확인(가입 화면 중복확인 버튼). 이미 사용 중이면 DUPLICATE_NICKNAME(409). */
    @Transactional(readOnly = true)
    fun ensureNicknameAvailable(nickname: String) {
        if (userRepository.existsByNickname(nickname)) {
            throw AppException(AuthErrorCode.DUPLICATE_NICKNAME, DUP_NICKNAME)
        }
    }

    /** 현재 사용자 조회. TenantContext에서 받은 userId로 격리. 가게 프로필을 함께 반환한다. */
    @Transactional(readOnly = true)
    fun me(userId: Long): UserResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { AppException(CommonErrorCode.UNAUTHORIZED) }
        val profile = userProfileRepository.findById(userId).orElse(null)?.toResponse()
        return UserResponse(
            id = userId,
            email = user.email,
            nickname = user.nickname,
            profile = profile,
        )
    }

    /**
     * 소셜 가입 후 이메일 보완. 형식 검증은 컨트롤러(@Valid)에서, 여기서는 중복 검사 후 저장한다.
     * 본인이 이미 같은 이메일을 갖고 있으면 멱등 통과, 다른 사용자가 사용 중이면 DUPLICATE(409).
     */
    @Transactional
    fun updateEmail(
        userId: Long,
        email: String,
    ): UserResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { AppException(CommonErrorCode.UNAUTHORIZED) }
        if (email != user.email && userRepository.existsByEmail(email)) {
            throw AppException(AuthErrorCode.DUPLICATE_EMAIL, DUP_EMAIL)
        }
        user.email = email
        userRepository.save(user)
        val profile = userProfileRepository.findById(userId).orElse(null)?.toResponse()
        return UserResponse(
            id = userId,
            email = user.email,
            nickname = user.nickname,
            profile = profile,
        )
    }

    /**
     * 토큰 갱신(회전). 동시·중복 호출 멱등 처리:
     * 같은 raw refresh 토큰으로 들어온 호출은 dedup 윈도 내에서 단 1회만 실제 회전하고, 나머지는 동일 결과를 받는다.
     * 윈도 밖 재사용은 회전된(ROTATED) 토큰이므로 INVALID_TOKEN으로 거부된다(회전 reuse 탐지 유지).
     * 멱등 비활성(ttl<=0) 시 매 호출 회전 → 즉시 재사용도 INVALID_TOKEN.
     *
     * @MX:ANCHOR: [AUTO] 웹 BFF의 모든 401 자동 재발급·미들웨어 선제 갱신이 도달하는 단일 갱신 진입점.
     */
    fun refresh(rawRefreshToken: String): TokenResponse {
        val cache = refreshDedup ?: return rotate(rawRefreshToken)
        // get(key, loader): 키당 로더를 원자적으로 최대 1회 실행. 동시 호출은 대기 후 같은 결과 수신.
        // 로더가 던진 예외(INVALID_TOKEN 등)는 그대로 전파되며 캐시에 저장되지 않는다.
        return cache.get(hashToken(rawRefreshToken)) { rotate(rawRefreshToken) }
    }

    /** 실제 회전. 캐시 로더 내부에서 호출되므로 TransactionTemplate으로 독립 트랜잭션을 보장한다. */
    private fun rotate(rawRefreshToken: String): TokenResponse =
        txTemplate.execute {
            val stored =
                refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                    ?: throw AppException(CommonErrorCode.INVALID_TOKEN)
            if (stored.status != RefreshTokenStatuses.ACTIVE || stored.expiresAt.isBefore(Instant.now())) {
                throw AppException(CommonErrorCode.INVALID_TOKEN)
            }
            val user =
                userRepository
                    .findById(stored.userId)
                    .orElseThrow { AppException(CommonErrorCode.INVALID_TOKEN) }

            // 회전: 사용된 refresh 토큰을 ROTATED로 무효화하고, 새 토큰이 세션 계보를 잇도록 부모로 넘긴다.
            stored.status = RefreshTokenStatuses.ROTATED
            stored.lastUsedAt = Instant.now()
            refreshTokenRepository.save(stored)
            issueTokens(user, parent = stored)
        }!!

    @Transactional
    fun logout(rawRefreshToken: String) {
        refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))?.let { token ->
            token.status = RefreshTokenStatuses.LOGGED_OUT
            token.lastUsedAt = Instant.now()
            refreshTokenRepository.save(token)
        }
        // 멱등 캐시 무효화: 로그아웃한 토큰이 dedup 윈도 내 캐시 히트로 재사용되지 않도록.
        refreshDedup?.invalidate(hashToken(rawRefreshToken))
    }

    /**
     * access + refresh 발급. refresh는 불투명 난수, DB엔 해시만 저장한다.
     * [parent]가 있으면(회전) 세션 계보를 잇는다: 세션 시작 시각 계승 + 재발급 횟수 +1 + 부모 id 기록.
     * 발급 컨텍스트(채널/기기/UA/IP)는 [ClientContext]에서 읽어 통계용으로 함께 저장한다.
     */
    private fun issueTokens(
        user: User,
        parent: RefreshToken? = null,
    ): TokenResponse {
        val userId = requireNotNull(user.id)
        val accessToken = tokenProvider.createAccessToken(userId, user.email)
        val rawRefresh = generateRefreshToken()
        val ctx = ClientContext.current()
        refreshTokenRepository.save(
            RefreshToken(
                userId = userId,
                tokenHash = hashToken(rawRefresh),
                expiresAt = Instant.now().plusSeconds(jwtProperties.refreshTtlSeconds),
                sessionStartedAt = parent?.sessionStartedAt ?: Instant.now(),
            ).apply {
                parentTokenId = parent?.id
                reissuedCount = parent?.let { it.reissuedCount + 1 } ?: 0
                clientId = ctx?.clientId
                deviceId = ctx?.deviceId
                userAgent = ctx?.userAgent
                createdIp = ctx?.ip
            },
        )
        return TokenResponse(
            accessToken = accessToken,
            refreshToken = rawRefresh,
            expiresIn = jwtProperties.accessTtlSeconds,
        )
    }

    private fun generateRefreshToken(): String {
        val bytes = ByteArray(REFRESH_TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(token: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val REFRESH_TOKEN_BYTES = 32

        // refresh 멱등 캐시 상한(동시 활성 세션 수 대비 충분히 큰 값). 초과 시 LRU 축출.
        const val REFRESH_DEDUP_MAX_SIZE = 10_000L

        // 중복 충돌 메시지(웹이 어떤 필드가 겹쳤는지 구분할 수 있도록 셋을 명확히 구별)
        const val DUP_IDENTITY = "이미 가입된 계정입니다"
        const val DUP_EMAIL = "이미 사용 중인 이메일입니다"
        const val DUP_NICKNAME = "이미 사용 중인 닉네임입니다"
    }
}
