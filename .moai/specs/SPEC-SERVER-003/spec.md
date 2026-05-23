# SPEC-SERVER-003 — 인증 (JWT + refresh rotation)

> status: DOING · deps: 002 · Phase 1 (M1 기반)

## 목표

자체 인증을 구축한다. Spring Security 기반 무상태 JWT(access + refresh 회전), BCrypt 비밀번호,
가입 시 사용자별 기본 설정 시드, JWT 필터 → `TenantContext`(멀티테넌시 1차 방어선) 주입.

## 범위 (In)

- **의존성**: `spring-boot-starter-security`, jjwt(api/impl/jackson), kotlin-jpa 플러그인.
- **스키마**: `V3__refresh_tokens.sql` — refresh 토큰 저장(원문 대신 SHA-256 해시, 만료/revoked).
- **보안 구성**: 무상태 `SecurityFilterChain`. 공개 경로(`/auth/**`, `/health`, `/actuator/**`, swagger), 그 외 인증 필요. CSRF/폼로그인/basic 비활성. BCrypt `PasswordEncoder`. 인증 실패 시 표준 JSON(디테일 비노출).
- **JWT**: `JwtTokenProvider`(HS256, 환경변수 서명키, 만료/위변조 검증), `JwtAuthenticationFilter`(Bearer 파싱 → SecurityContext + TenantContext, 요청 종료 시 clear).
- **TenantContext**: 요청 스코프(ThreadLocal) userId. 미설정 시 `UNAUTHORIZED`.
- **엔드포인트**: `POST /auth/signup`(가입+시드+토큰), `/auth/login`, `/auth/refresh`(회전), `/auth/logout`(무효화), `GET /me`(보호 — 필터/TenantContext 검증용).
- **가입 시드**: 매출 카테고리 11 · 매출 결제방식 4 · 지출 카테고리 7 · 지출 결제방식 3 · 카드사 9. 멱등(ON CONFLICT DO NOTHING).
- **에러 인프라(경량)**: `ErrorCode`/`AppException`/`ErrorResponse`/`GlobalExceptionHandler`. (SPEC-004에서 Discord·확장.)
- **JPA**: `User`/`RefreshToken` 엔티티, `ddl-auto=validate`(엔티티-스키마 정합 강제).

## 범위 밖 (Out)

- 소셜 로그인(카카오/구글) — 인터페이스 자리만 염두, 미구현.
- 비밀번호 재설정/이메일 인증.
- Discord 에러 리포팅·전체 @ControllerAdvice (→ SPEC-004).
- 설정 CRUD API(→ SPEC-012) — 여기서는 가입 시 시드만(JdbcTemplate, 엔티티 미생성).

## 인수 기준

1. `./gradlew build test` 통과(엔티티-스키마 validate 포함).
2. 가입: 사용자 생성 + BCrypt 해시 저장(평문 아님) + 기본 설정 시드(11/4/7/3/9) + 토큰 발급.
3. 로그인: 비밀번호 검증, 오답은 `INVALID_CREDENTIALS`(401).
4. refresh 회전: 사용 시 새 토큰 발급, **이전 refresh는 즉시 무효(401)**.
5. 로그아웃: refresh 무효화 → 이후 refresh 401.
6. 보호 엔드포인트(`/me`): 토큰 없음/위변조 → 401, 유효 토큰 → 200 + 본인 정보.
7. 중복 이메일 409, 잘못된 입력 400.
8. 서명키·TTL은 `${ENV}` 참조(코드/깃에 실제 시크릿 없음).

## 설계 메모

- access=JWT(짧은 TTL 기본 15분), refresh=불투명 난수(32바이트) + DB에 SHA-256 해시 저장 → 무효화 가능.
- 멀티테넌시: 데이터 쿼리는 항상 `TenantContext.currentUserId()`로 격리(도메인 SPEC에서 강제). 필터가 set/clear.
- 카드사 기본값은 원본에 없어 국내 주요 발급사 9개를 표준값(수수료 2.0%/입금 3영업일)으로 서버가 시드.
- 검증은 Zonky 임베디드 PostgreSQL(실제 PG, Docker 불필요)에서 실제 흐름으로 수행.
