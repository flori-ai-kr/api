# SPEC-SERVER-018 — 리치 OpenAPI 어노테이션 (E1)

## 목표
flori-ai/mobile이 계약(contract)으로 읽는 Swagger 문서의 품질을 높인다. JWT 인증 스킴을 전역 등록하고, 핵심 DTO에 `@Schema`(설명·예시·허용값)를 부여해 앱이 필드 의미·예시를 바로 파악하도록 한다.

## 배경
- 출처: `socc-assistant-api`의 `@Schema`(example/allowableValues)·`SwaggerConfiguration`(bearer-jwt SecurityScheme) 패턴.
- 기존 flori: `@Operation` 요약만 있고 **JWT 보안 스킴 미등록**(Swagger Authorize 버튼 없음), DTO 필드 설명/예시 없음.

## 범위 (전 DTO가 아닌 핵심 + 공통, 패턴 정착)
- `OpenApiConfig`: JWT **bearer 보안 스킴 전역 등록** + `addSecurityItem` → Authorize 버튼 + 보호 엔드포인트 계약 노출.
- `ErrorResponse`: `@Schema`로 표준 에러 계약 문서화(모든 4xx/5xx 공통).
- `AuthDtos`(앱 진입점): Signup/Login/Refresh/Logout/Token 전 필드 `@Schema(description, example)`.
- `SaleDtos`(핵심 도메인): `SaleCreateRequest` 요청 필드 + `SaleResponse`의 **서버 계산(SSOT) 필드**(fee/expectedDeposit/expectedDepositDate/depositStatus) `@Schema`.
- 나머지 도메인 DTO는 동일 패턴으로 점진 적용(이 SPEC에서 예시·컨벤션 정착).

## 인수기준
- [x] OpenApiConfig에 bearerAuth(HTTP/bearer/JWT) 스킴 + 전역 SecurityRequirement
- [x] ErrorResponse·AuthDtos·SaleDtos 핵심 필드 `@Schema`
- [x] `./gradlew build test` 그린 — 167 테스트(0 실패/0 스킵)
- [x] 동작 변경 0(문서 메타데이터만)

## 비고 / 함정
- Kotlin 블록주석은 **중첩**된다. KDoc 본문에 `/**`나 `/*`를 만드는 문자열(예: `/webhooks/**`)을 넣으면 "Unclosed comment"가 난다 → 경로 예시는 별표 글롭 없이 표기. (이번에 실제로 겪어 수정)
- 전역 SecurityRequirement는 공개 엔드포인트에도 자물쇠를 표시하지만, 실제 접근 허용은 `SecurityConfig`가 결정(표시상 cosmetic).
