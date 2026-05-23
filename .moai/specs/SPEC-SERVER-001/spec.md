# SPEC-SERVER-001 — 프로젝트 스켈레톤

> status: DOING · deps: 없음 · Phase 1 (M1 기반)

## 목표

Hazel Server의 빈 Spring Boot(Kotlin) 프로젝트를 부팅 가능한 상태로 세운다.
이후 모든 SPEC이 이 스켈레톤 위에 도메인 패키지를 추가하는 것으로 끝나도록(기존 코드 수정 최소화) 골격·횡단 관심사 자리·품질 게이트를 마련한다.

## 범위 (In)

- **빌드**: Gradle (Kotlin DSL) + Spring Boot 3.x + Kotlin 2.x, Java 21 toolchain, Gradle Wrapper 동봉.
- **패키지 구조**: `com.hazel` 루트 + `com.hazel.common`(config/security/tenant/error/storage/push 자리). 도메인 패키지는 후속 SPEC에서 추가.
- **부팅**: `HazelServerApplication` 메인 + `application.yml`(모든 외부 값은 `${ENV}` 참조).
- **헬스체크**: `GET /health` → 200 + `{status, service, time}` (DB 의존 없음). Actuator `/actuator/health` 포함.
- **API 문서**: springdoc-openapi(Swagger UI) 노출, OpenAPI 메타(title/description) 설정.
- **품질 게이트**: ktlint(official) + detekt 구성, `./gradlew build`에 ktlint 연동.
- **컨테이너**: 멀티스테이지 `Dockerfile`(temurin 21).
- **`.gitignore`**: Gradle/IDE/secret 제외(이미 존재 — 검증만).

## 범위 밖 (Out)

- DB/JPA/Flyway 연결 (→ SPEC-SERVER-002). 이 SPEC은 datasource 의존성을 추가하지 않는다(없어도 컨텍스트가 떠야 함).
- 인증/Security (→ SPEC-SERVER-003). Security 스타터 미추가(헬스/스웨거 공개 유지).
- S3/FCM/Discord/CORS 실제 구현 (→ SPEC-SERVER-004).

## 인수 기준

1. `./gradlew build test` 가 성공한다(ktlint·detekt 게이트 포함).
2. `@SpringBootTest` 컨텍스트 로딩 테스트가 DB 없이 통과한다.
3. `GET /health` 가 200과 `status: "UP"`, `service: "hazel-server"` 를 반환한다(`@WebMvcTest` 검증).
4. `application.yml` 에 평문 시크릿이 없다(모든 외부 값은 `${ENV}` 참조).
5. 패키지 구조가 `com.hazel` / `com.hazel.common` 으로 잡혀 있어 후속 도메인 패키지를 추가만 하면 된다.
6. Swagger UI(`/swagger-ui.html`)와 OpenAPI 문서(`/v3/api-docs`)가 노출된다.

## 설계 메모

- 헬스 응답도 DTO(`HealthResponse`)로 반환 — "엔티티/원시 Map을 컨트롤러 밖으로 노출하지 않는다" 원칙의 본보기.
- detekt는 main 소스셋만 분석(테스트 컨벤션과 충돌 회피), Spring 관용구(`runApplication(*args)`)와 충돌하는 `SpreadOperator`는 비활성.
- 버전: Spring Boot 3.4.x / Kotlin 2.1.x / Gradle Wrapper 8.11.x / springdoc 2.7.x / ktlint plugin 12.1.x / detekt 1.23.x.
