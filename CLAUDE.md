# Flori Server — 꽃집 관리 백엔드 API

Flori(꽃집 어드민)의 모바일/웹 백엔드. Spring Boot(Kotlin) REST API.
자매 repo: `~/Desktop/flori-ai/mobile`(React Native / Expo), `~/Desktop/flori-ai/web`(Next.js).

## 이 repo의 역할

기존 Next.js 웹의 비즈니스 로직을 **앱/웹이 호출 가능한 REST API**로 재구현하고, 자체 AWS 인프라 위에서 동작시킨다.

## 기술 스택

| 영역 | 선택 |
|------|------|
| 언어/빌드 | Kotlin + Gradle (Kotlin DSL) |
| 프레임워크 | Spring Boot 3.x (Java 21 toolchain) |
| 데이터 접근 | Spring Data JPA + Hibernate (jsonb/배열은 hypersistence-utils), 통계 집계는 네이티브 SQL |
| DB | AWS RDS PostgreSQL (로컬: Docker `flori-pg`) |
| 스키마 관리 | **DDL 직접 관리** — 스키마 변경은 SQL DDL로 수행, `ddl-auto: validate`로 엔티티↔DB 정합 검증 |
| 인증 | Spring Security + 자체 JWT(access + refresh rotation) + **registerToken**(가입 대기, 5분). **소셜 전용**(카카오·구글·네이버 OAuth), 비밀번호 없음 |
| 검증 | Jakarta Bean Validation |
| 스토리지 | AWS S3 + CloudFront (presigned PUT URL 발급) |
| 푸시 | FCM (Firebase Admin SDK) |
| 스케줄 | Spring `@Scheduled` |
| 에러 | `@ControllerAdvice` 표준 응답(`E-{DOMAIN}-{NNN}` 코드 체계) + Discord 웹훅 |
| API 문서 | Spring REST Docs → OpenAPI 3 (swagger-ui) |
| 커버리지 | JaCoCo line 80% 게이트 |

## 아키텍처 원칙 (HARD)

- **클린 아키텍처**: `controller → service → repository`. 도메인별 패키지(`kr.ai.flori.<domain>`). 횡단 관심사는 `kr.ai.flori.common/`(security, error, tenant, storage, push, config). 도메인 에러 코드는 `<domain>/error/`.
- **DTO 경계**: 엔티티를 컨트롤러 밖으로 노출하지 않는다. 요청/응답 DTO 분리.
- **멀티테넌시 = 보안 1순위** [HARD]: 모든 데이터 쿼리는 JWT에서 추출한 `userId`(`TenantContext`)로 격리한다. `user_id` 필터 누락은 곧 데이터 유출. RLS가 없으므로 애플리케이션이 유일한 방어선. 신원은 요청 본문이 아닌 토큰/TenantContext에서만 도출한다.
- **검증은 시스템 경계에서**: 컨트롤러 진입점 `@Valid`.
- **계산은 서버가 SSOT**: 지출총액(`unit_price * quantity`) 등은 서버가 계산해 응답.
- **시크릿은 환경변수**: 코드/깃에 시크릿 금지. `application.yml`은 `${ENV}` 참조만.

## 보안 체크리스트 (HARD)

- JWT: 짧은 access TTL + refresh rotation, registerToken 5분, 서명키 환경변수, 만료/위변조 검증
- 멀티테넌시: 모든 쿼리 `user_id` 격리
- 입력 검증: Bean Validation, SQL 인젝션 방지(JPA·네이티브 쿼리 파라미터 바인딩)
- S3: presigned URL 짧은 만료, 소유권 검증 후 발급
- CORS: 앱/웹 origin 화이트리스트
- 에러 응답: 내부 디테일(스택/쿼리) 노출 금지, 일반 메시지 + Discord에만 상세
- OWASP Top 10 준수

## 커밋 규칙
- `git add -A` 금지 → 변경 파일만 명시 추가
- conventional commits, 한국어 메시지
- 빌드/테스트 통과 후에만 커밋(`./gradlew build test`)
- Co-Authored-By: Claude <noreply@anthropic.com>
- force push 금지

## 참고 문서
- `docs/ARCHITECTURE.md` — 아키텍처
- `docs/DATABASE.md` — DB 스키마 명세 (SSOT)
- `docs/DESIGN.md` — 설계
- `docs/ERROR_CODES.md` — 에러 코드 체계

## 문서화 규칙
- 모든 문서는 한국어로 작성한다. 코드/식별자/함수명/타입은 영어.
- 코드와 문서를 함께 갱신한다.
