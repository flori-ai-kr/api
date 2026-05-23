# SPEC-SERVER-004 — 공통 인프라

> status: DOING · deps: 003 · Phase 1 (M1 기반)

## 목표

도메인 SPEC들이 공유할 횡단 인프라를 완성한다: 표준 에러 응답 + Discord 리포팅, S3 presigned 발급, FCM 푸시,
CORS, 보안 헤더. 외부 자격증명이 없어도 로컬/테스트에서 컨텍스트가 부팅되도록 graceful conditional 구성.

## 범위 (In)

- **에러 인프라 확장**: `GlobalExceptionHandler`에 핸들러 보강(검증/제약위반/DataIntegrity→409/AccessDenied→403/일반예외→500).
  예기치 못한 예외만 **Discord 웹훅 비동기 리포팅**(`@Async`) + 일반 메시지로 교체(내부 디테일 비노출).
- **DiscordErrorReporter**: 웹훅 미설정 시 콘솔 폴백, 5분 중복 제거, 스택/PII(경로·이메일·토큰·비밀번호·키) 새니타이즈·잘라내기.
- **S3 presigned**: `S3PresignService.presignUpload(key, contentType)` → presigned PUT URL + CloudFront(또는 S3) 파일 URL. `S3Presigner` 빈(지연 자격증명).
- **FCM 푸시**: `PushService` 추상화 + `FirebasePushService`(fcm.enabled=true 시) + `LoggingPushService`(폴백). 영구실패 토큰 식별(tokenInvalid).
- **CORS**: origin 화이트리스트(env), 보안필터 체인 연동.
- **보안 헤더**: `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`(기본), `Referrer-Policy`.
- 모든 외부 값은 `${ENV}` 참조.

## 범위 밖 (Out)

- presigned 발급 **엔드포인트**·소유권/메타 검증·키 생성 규칙 (→ SPEC-010 사진).
- 푸시 **구독 저장/스케줄 발송** (→ SPEC-008/012).
- rate limiting(고려만, 후속).

## 인수 기준

1. `./gradlew build test` 통과. AWS/FCM 자격증명 없이 컨텍스트 부팅(빈 conditional/폴백).
2. AppException → 매핑된 상태코드/코드, Discord 미전송.
3. 예기치 못한 예외 → 500 + 일반 메시지(내부 디테일·시크릿 비노출) + Discord 리포팅 호출.
4. 스택 새니타이즈: 경로/이메일/토큰/비밀번호/키 마스킹, 줄 수 제한.
5. S3 presign: 오프라인 서명으로 presigned PUT URL + CloudFront 파일 URL 발급. 버킷 미설정 시 명확한 예외.
6. 보안 헤더(nosniff/DENY/Referrer-Policy)가 응답에 포함.
7. 허용 origin CORS 프리플라이트 통과(Access-Control-Allow-Origin 반영).
8. FCM 폴백(LoggingPushService) 정상 동작(예외 없이 success).

## 설계 메모

- Discord 전송은 `@Async`로 응답 스레드와 분리. 미설정/실패는 graceful(콘솔 로그).
- S3Presigner는 무조건 빈으로 생성(빌드는 자격증명 불필요), 실제 발급은 SPEC-010에서 호출. presign은 로컬 서명이라 테스트에서 실제 검증 가능.
- FCM은 `fcm.enabled=true`에서만 FirebaseApp 초기화. 그 외에는 `@ConditionalOnMissingBean` 로깅 폴백 → 항상 PushService 빈 존재.
- CORS/헤더는 SPEC-003 `SecurityConfig`에 통합(횡단 관심사 일원화).
