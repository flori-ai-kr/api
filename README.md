# Hazel Server

> 꽃집 어드민 **Hazel**의 모바일 앱 백엔드 — Spring Boot(Kotlin) REST API

![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-RDS-4169E1?logo=postgresql&logoColor=white)
![Tests](https://img.shields.io/badge/tests-145%20passing-2e7d32)

기존 Next.js + Supabase 웹앱의 비즈니스 로직을, 네이티브 앱(Flutter)이 호출할 수 있는 **REST API**로 재구현하고 자체 AWS 인프라 위에 올린 백엔드다. 자매 repo: `~/Desktop/hazel-app`(Flutter), 원본 웹 `~/Desktop/hazel-admin`(참고용).

---

## 목차

- [Quick Start](#quick-start)
- [환경 설정](#환경-설정)
- [프로젝트 구조](#프로젝트-구조)
- [핵심 개념](#핵심-개념)
- [개발 가이드](#개발-가이드)
- [테스트](#테스트)
- [문서](#문서)
- [진행 상태](#진행-상태)

---

## Quick Start

### 요구사항

- **Java 21+** (Gradle 래퍼 동봉 — 별도 Gradle 설치 불필요)
- 실행 시에만 **PostgreSQL** (테스트는 임베디드 PG라 DB 불필요)

### 빌드 & 테스트 (DB 없이 가능)

```bash
./gradlew build test     # ktlint + detekt + 전체 테스트(임베디드 PostgreSQL)
./gradlew ktlintFormat   # 코드 자동 포맷
```

> 테스트는 [Zonky 임베디드 PostgreSQL](docs/ARCHITECTURE.md#zonky-임베디드-postgresql-테스트)을 띄워 실제 DB에서 검증하므로, 로컬에 DB가 없어도 `./gradlew test`가 동작한다.

### 로컬 실행

```bash
# 1) 로컬 PostgreSQL (예: Docker)
docker run -d --name hazel-pg -e POSTGRES_DB=hazel -e POSTGRES_USER=hazel \
  -e POSTGRES_PASSWORD=hazel -p 5432:5432 postgres:16

# 2) 서버 실행 (기본 프로필 local — 미설정 환경변수는 graceful 폴백)
./gradlew bootRun

# 3) API 계약(Swagger) 확인
open http://localhost:8080/swagger-ui.html
```

Flyway가 부팅 시 스키마를 자동 적용한다. 헬스체크: `GET /health`.

---

## 환경 설정

모든 시크릿/외부 설정은 코드가 아닌 **환경변수**로 주입한다(`application.yml`은 `${ENV}` 참조만). 미설정 시 로컬에서 graceful하게 동작한다.

| 변수 | 용도 | 미설정 시 |
|------|------|-----------|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | PostgreSQL 연결 | `localhost:5432/hazel` |
| `JWT_SECRET` | JWT 서명키(운영 필수, ≥32바이트) | 로컬 전용 기본값 ⚠️ 비-로컬 프로필에선 부팅 거부 |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | 토큰 만료(초) | 900 / 1209600 |
| `AWS_REGION` / `S3_BUCKET` / `CLOUDFRONT_DOMAIN` | S3 presigned 업로드 | 미발급(presign 시 에러) |
| `FCM_ENABLED` / `FCM_CREDENTIALS` | FCM 푸시 | 로깅 폴백(no-op) |
| `DISCORD_WEBHOOK_URL` | 운영 에러 알림 | 콘솔 로깅 |
| `INTERNAL_API_KEY` | 내부 수집 API 인증 | 내부 API 전면 차단 |
| `CORS_ALLOWED_ORIGINS` | 앱/웹 origin 화이트리스트(콤마 구분) | localhost:3000,8081 |
| `SPRING_PROFILES_ACTIVE` | 프로필 | `local` |

> **운영 배포 체크리스트**: `JWT_SECRET`은 반드시 강한 값으로 설정(미설정 시 비-로컬 프로필 부팅 실패), Swagger 비활성(`springdoc.swagger-ui.enabled=false`), 레이트리밋 도입을 검토한다.

---

## 프로젝트 구조

```
com.hazel
├── common/                  # 횡단 관심사 (도메인 무관)
│   ├── config/              # CORS · Async · Schedule · OpenAPI
│   ├── security/            # JWT · 필터 · SecurityConfig · 내부 API 인증
│   ├── tenant/              # TenantContext (요청 스코프 userId)
│   ├── error/               # AppException · 표준 에러 · Discord 리포팅
│   ├── storage/             # S3 presigned 발급
│   ├── push/                # FCM 추상화
│   ├── domain/              # 공용 도메인 상수 (PaymentMethods 등)
│   └── util/                # 공용 유틸 (KST · monthRange)
├── auth/                    # 인증 (회원가입·로그인·refresh 회전·/me)
├── sales/                   # 매출 (+ 서버 입금계산)
├── expenses/                # 지출 + 고정비 (@Scheduled 자동생성)
├── customers/               # 고객 (findOrCreate · 실시간 통계)
├── reservations/ · calendar/# 예약(매출 전환·픽업) · 캘린더 (리마인더 푸시)
├── deposits/                # 카드 입금대조
├── photos/                  # 사진첩 (presigned 업로드) · 태그
├── insights/                # 트렌드/인스타 공유 읽기 · 스크랩 · 내부 수집
├── settings/                # 카드사 · 매출/지출 설정 · 하단바 · 푸시 구독
└── dashboard/               # 오늘/월 집계 · 네이티브 SQL 통계
```

각 도메인은 `controller → service → repository` 레이어를 따른다. 자세한 패턴은 [docs/PATTERNS.md](docs/PATTERNS.md).

---

## 핵심 개념

| 개념 | 한 줄 요약 |
|------|-----------|
| **멀티테넌시 (보안 1순위)** | RLS 없이 **애플리케이션이 유일한 방어선**. 모든 쿼리를 `TenantContext.currentUserId()`로 격리. |
| **서버가 계산 SSOT** | 카드수수료·입금예정일·지출총액은 서버가 계산해 응답. 앱은 표시만. |
| **자체 JWT** | access=짧은 JWT(HS256), refresh=불투명 난수+DB 해시 저장(회전). BCrypt. |
| **스케줄** | 고정비 자동생성·예약 리마인더·일일 요약을 `@Scheduled`(KST)로 처리(Vercel Cron 대체). |
| **계약(contract)** | `springdoc-openapi` Swagger가 앱이 읽는 API 계약의 출처. |

전체 그림과 "왜 이 기술인가"는 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## 개발 가이드

- **검증 게이트**: 커밋 전 `./gradlew build test` 통과 필수(ktlint + detekt + 전체 테스트).
- **포맷**: `./gradlew ktlintFormat`로 자동 교정. 라인 길이는 ktlint가 관장.
- **새 도메인 추가**: [docs/PATTERNS.md](docs/PATTERNS.md#새-도메인-추가-recipe)의 단계별 레시피를 따른다("기존 코드 수정 없이 패키지 추가"가 목표).
- **Kotlin이 처음이라면**: [docs/KOTLIN.md](docs/KOTLIN.md)에서 이 repo가 쓰는 Kotlin/Spring 관용구를 실제 코드로 설명한다.
- **커밋**: conventional commits(한국어). `git add -A` 금지 → 변경 파일만 명시.

---

## 테스트

- **145개 테스트, 0 스킵.** `@SpringBootTest` 통합 테스트는 Zonky 임베디드 PostgreSQL에서 실제 마이그레이션·쿼리를 실행한다.
- **멀티테넌시 격리 테스트**가 모든 도메인에 필수로 포함된다(다른 user 데이터 접근 차단).
- 계산/규칙(카드수수료·영업일·고정비 발생 판정)은 순수 함수 단위 테스트로 검증.

```bash
./gradlew test                                   # 전체
./gradlew test --tests 'com.hazel.sales.*'       # 특정 패키지
```

---

## 문서

| 문서 | 내용 |
|------|------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 아키텍처 + 기술 선정 이유 (Mermaid 다이어그램) |
| [docs/PATTERNS.md](docs/PATTERNS.md) | 레이어 패턴 · 멀티테넌시 · **새 도메인 추가 레시피** · 컨벤션 |
| [docs/KOTLIN.md](docs/KOTLIN.md) | 이 repo의 Kotlin/Spring 관용구 (입문자용 상세) |
| [docs/DESIGN.md](docs/DESIGN.md) | 설계 SSOT (배경·범위) |
| [ROADMAP.md](ROADMAP.md) | SPEC 목록·상태 |
| [HANDOFF.md](HANDOFF.md) | 세션 인수인계·운영 준비물 |

---

## 진행 상태

- ✅ **Phase 1 (M1 기반 + M2 도메인) — 13개 SPEC 전부 완료.** 백엔드 REST API 완성, 앱 연동 준비 완료.
- ⏸ **Phase 2 (구독/결제, RevenueCat 웹훅)** — 앱 출시(M4) 후 진행.
