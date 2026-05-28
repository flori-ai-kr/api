package kr.ai.flori.auth.service

import kr.ai.flori.auth.dto.OAuthResult
import kr.ai.flori.auth.dto.RegisterCompleteRequest
import kr.ai.flori.auth.dto.TokenResponse
import kr.ai.flori.auth.dto.UserResponse
import kr.ai.flori.auth.entity.RefreshToken
import kr.ai.flori.auth.entity.User
import kr.ai.flori.auth.oauth.SocialOAuthClient
import kr.ai.flori.auth.repository.RefreshTokenRepository
import kr.ai.flori.auth.repository.UserRepository
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import kr.ai.flori.common.security.JwtProperties
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.user.entity.UserProfile
import kr.ai.flori.user.repository.UserProfileRepository
import kr.ai.flori.user.service.toResponse
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
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
) {
    private val secureRandom = SecureRandom()

    /**
     * 소셜 로그인 일반화 진입점. provider(KAKAO/GOOGLE/NAVER)로 클라이언트를 선택해 인증코드를 검증한다.
     * - 기존 신원 → 로그인 토큰 발급(registered=true).
     * - 신규 신원 → User를 만들지 않고 registerToken + 소셜 기본값 반환(registered=false).
     * 네이버는 state가 필수다.
     *
     * @MX:ANCHOR: [AUTO] 모든 소셜 OAuth 엔드포인트(kakao/google/naver)가 의존하는 단일 진입점.
     * @MX:REASON: 컨트롤러 3개 경로 + register/complete 신원 계약의 출처(fan_in>=3). 반환 계약 변경은 웹 세션에 파급된다.
     */
    @Transactional
    fun oauthLogin(
        provider: String,
        code: String,
        redirectUri: String,
        state: String?,
    ): OAuthResult {
        val client =
            socialClients[provider]
                ?: throw AppException(ErrorCode.VALIDATION, "지원하지 않는 소셜 제공자입니다: $provider")
        val info = client.authenticate(code, redirectUri, state)

        val existing = userRepository.findByProviderAndProviderId(info.provider, info.providerId)
        if (existing != null) {
            if (!existing.isActive) {
                throw AppException(ErrorCode.FORBIDDEN, "비활성화된 계정입니다")
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
                ?: throw AppException(ErrorCode.INVALID_TOKEN, "가입 토큰이 유효하지 않거나 만료되었습니다")
        verifyRegisterable(principal.provider, principal.providerId, request.email)

        val user =
            try {
                // saveAndFlush: 시더의 raw JDBC INSERT가 같은 트랜잭션에서 user FK를 참조하므로 즉시 flush
                userRepository.saveAndFlush(
                    User(
                        email = request.email,
                        name = request.nickname,
                        provider = principal.provider,
                        providerId = principal.providerId,
                    ),
                )
            } catch (_: DataIntegrityViolationException) {
                // 동시 가입 경쟁(같은 신원/이메일 unique 충돌): 멱등하게 이미 가입됨으로 처리
                throw AppException(ErrorCode.DUPLICATE, "이미 가입된 계정입니다")
            }
        val userId = requireNotNull(user.id)

        userProfileRepository.save(
            UserProfile(
                userId = userId,
                storeName = request.storeName,
                regionSido = request.regionSido,
            ).apply {
                regionSigungu = request.regionSigungu
                ownerAgeRange = request.ownerAgeRange
                interests = request.interests?.toTypedArray() ?: emptyArray()
                specialties = request.specialties?.toTypedArray() ?: emptyArray()
            },
        )
        seeder.seedForNewUser(userId)
        return issueTokens(user)
    }

    /**
     * 가입 가능 여부 검증(자동 병합 금지 정책).
     * - 같은 (provider, providerId)가 이미 가입 → DUPLICATE(registerToken 재사용 차단).
     * - 이메일이 타 계정 사용 중 → DUPLICATE(이메일 선점 공격 시 다른 계정에 붙이지 않는다).
     */
    private fun verifyRegisterable(
        provider: String,
        providerId: String,
        email: String,
    ) {
        if (userRepository.findByProviderAndProviderId(provider, providerId) != null) {
            throw AppException(ErrorCode.DUPLICATE, "이미 가입된 계정입니다")
        }
        if (userRepository.existsByEmail(email)) {
            throw AppException(ErrorCode.DUPLICATE, "이미 사용 중인 이메일입니다")
        }
    }

    /** 현재 사용자 조회. TenantContext에서 받은 userId로 격리. 가게 프로필을 함께 반환한다. */
    @Transactional(readOnly = true)
    fun me(userId: Long): UserResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { AppException(ErrorCode.UNAUTHORIZED) }
        val profile = userProfileRepository.findById(userId).orElse(null)?.toResponse()
        return UserResponse(
            id = userId,
            email = user.email,
            name = user.name,
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
                .orElseThrow { AppException(ErrorCode.UNAUTHORIZED) }
        if (email != user.email && userRepository.existsByEmail(email)) {
            throw AppException(ErrorCode.DUPLICATE, "이미 사용 중인 이메일입니다")
        }
        user.email = email
        userRepository.save(user)
        val profile = userProfileRepository.findById(userId).orElse(null)?.toResponse()
        return UserResponse(
            id = userId,
            email = user.email,
            name = user.name,
            profile = profile,
        )
    }

    @Transactional
    fun refresh(rawRefreshToken: String): TokenResponse {
        val stored =
            refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                ?: throw AppException(ErrorCode.INVALID_TOKEN)
        if (stored.revoked || stored.expiresAt.isBefore(Instant.now())) {
            throw AppException(ErrorCode.INVALID_TOKEN)
        }
        val user =
            userRepository
                .findById(stored.userId)
                .orElseThrow { AppException(ErrorCode.INVALID_TOKEN) }

        // 회전: 사용된 refresh 토큰 무효화 후 재발급
        stored.revoked = true
        refreshTokenRepository.save(stored)
        return issueTokens(user)
    }

    @Transactional
    fun logout(rawRefreshToken: String) {
        refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))?.let { token ->
            token.revoked = true
            refreshTokenRepository.save(token)
        }
    }

    private fun issueTokens(user: User): TokenResponse {
        val userId = requireNotNull(user.id)
        val accessToken = tokenProvider.createAccessToken(userId, user.email)
        val rawRefresh = generateRefreshToken()
        refreshTokenRepository.save(
            RefreshToken(
                userId = userId,
                tokenHash = hashToken(rawRefresh),
                expiresAt = Instant.now().plusSeconds(jwtProperties.refreshTtlSeconds),
            ),
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
    }
}
