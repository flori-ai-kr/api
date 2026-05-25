# SPEC-SERVER-015 — Spring Boot 3.5 업그레이드

## 목표
EOL(2025-12-31 만료)된 Spring Boot 3.4 라인에서 **현재 OSS 지원되는 3.5 최신 패치**로 올려 보안 패치 공백을 해소한다. 동작 보존(behavior-preserving) — 기능 변경 없음.

## 배경
- Spring Boot 3.4는 2025-12-31자로 OSS 지원 종료(EOL) → 신규 CVE 패치를 못 받음.
- hazel CLAUDE.md "보안 1순위" 원칙과 충돌하므로 지원 라인으로 이동 필요.
- 3.5는 Spring Framework 6 유지(호환성 깨짐 최소) → 저위험 경로. (4.0은 Jackson 2→3·SF7 등 breaking change가 있어 후속 과제로 분리.)

## 범위
- `org.springframework.boot` 플러그인 `3.4.1` → `3.5.14` (3.5 최신 패치, 2026-04 릴리스).
- `springdoc-openapi-starter-webmvc-ui` `2.7.0` → `2.8.17` (3.4 타깃 → 3.5 타깃 라인으로 동반 상향).
- 문서의 버전 표기 갱신(`docs/ARCHITECTURE.md`, `HANDOFF.md`).
- 그 외 의존성은 Spring Boot BOM이 관리 → 변경 없음. hypersistence-utils-hibernate-63 3.9.0은 3.5의 Hibernate 6.6과 호환 확인 → 유지.
- Gradle 8.11.1은 3.5 플러그인(8.4+ 요구) 호환 → 변경 없음.

## 인수기준
- [x] build.gradle.kts: Spring Boot 3.5.14 / springdoc 2.8.17 반영
- [x] `./gradlew build test` 전체 그린 — 165 테스트 통과(0 실패/0 에러/0 스킵)
- [x] runtimeClasspath에서 `spring-boot-starter-web -> 3.5.14` 해석 확인
- [x] 문서 버전 표기 갱신(ARCHITECTURE/HANDOFF)
- [x] 기능 변경 0 (behavior-preserving)

## 검증 결과
- `./gradlew build test`: BUILD SUCCESSFUL (ktlint·detekt 포함), 165 tests / 0 failures.
- 컨텍스트 기동 정상(통합 테스트가 embedded-postgres + 전체 빈 로딩으로 검증).

## 후속 과제(별도 SPEC)
- Spring Boot 4.0 마이그레이션(Jackson 3, Spring Framework 7) — 3.5 OSS 종료(2026-06-30) 전후로 계획.
- Kotlin 2.1.0 → 2.2+ 상향(롤링 릴리스, 보안 급함 아님).
