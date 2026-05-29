# Flori Server — 에러 코드 표

> 최종 업데이트: 2026-05-28

표준 에러 응답 바디의 `code` 필드는 안정적인 `E-{DOMAIN}-{NNN}` 식별자다.
웹/앱 클라이언트는 **메시지 텍스트가 아니라 이 `code` 값으로 에러를 분기**한다.
한 번 공개된 `code`는 바꾸지 않는다(클라이언트 호환성).

## 응답 형식

모든 에러는 `@ControllerAdvice`(`GlobalExceptionHandler`)가 아래 형태로 변환한다.

```json
{
  "code": "E-AUTH-002",
  "message": "이미 사용 중인 이메일입니다"
}
```

- `code`: 안정적 식별자(분기용).
- `message`: 사용자 노출용 메시지. 호출부가 더 구체적 메시지를 줄 수 있어 같은 `code`라도 `message`는 다를 수 있다.
- 내부 디테일(스택/쿼리)은 절대 노출하지 않는다. 5xx는 일반 메시지로 교체 후 Discord에만 상세 전송.

## 코드 소유 규칙 (도메인별)

| 구분 | 위치 | 설명 |
|------|------|------|
| 공통(횡단) 코드 | `common/error/CommonErrorCode` | 특정 도메인에 속하지 않고 인프라(JWT 필터, 검증, 제네릭 핸들러)에서 쓰이는 코드 |
| 도메인 전용 코드 | `<domain>/error/<Domain>ErrorCode` | 도메인 고유 의미가 필요한 코드. 예: `auth/error/AuthErrorCode` |

- 모든 에러 코드 enum은 `common/error/ErrorCode` **인터페이스**(`code`, `status`, `defaultMessage`)를 구현한다.
- `AppException(errorCode: ErrorCode, ...)`은 인터페이스를 받으므로 공통/도메인 코드를 모두 던질 수 있다.
- 새 도메인은 자신의 `<domain>/error` 패키지에 enum을 추가한다(기존 코드 수정 없이 확장).

## E-CMN-* (공통)

| 코드 | 의미 | HTTP | 비고 |
|------|------|------|------|
| `E-CMN-001` | VALIDATION (입력값 검증 실패) | 400 | `@Valid`, 제약 위반, 본문 파싱 실패, 도메인 값 검증 |
| `E-CMN-002` | UNAUTHORIZED (인증 필요) | 401 | 미인증 접근, 세션/사용자 없음 |
| `E-CMN-003` | INVALID_TOKEN (토큰 무효·만료) | 401 | access/register/refresh 토큰 무효·만료, OAuth 교환 실패 |
| `E-CMN-004` | FORBIDDEN (권한 없음) | 403 | 접근 거부, 비활성 계정, 구독 필요 기능 |
| `E-CMN-005` | NOT_FOUND (대상 없음) | 404 | 리소스 미존재 |
| `E-CMN-006` | CONFLICT (일반 충돌) | 409 | 의미가 특정되지 않은 일반 409. 도메인 일반 중복(전화번호·태그·계정·라벨 등) 및 `DataIntegrityViolationException` 폴백 |
| `E-CMN-999` | INTERNAL (서버 오류) | 500 | 예기치 못한 예외. 일반 메시지 + Discord 리포팅 |

## E-AUTH-* (인증/가입)

자동 병합 금지 정책상 중복을 신원/이메일/닉네임으로 **코드로 구분**한다.

| 코드 | 의미 | HTTP | 발생 시나리오 |
|------|------|------|----------------|
| `E-AUTH-001` | ALREADY_REGISTERED (이미 가입된 신원) | 409 | 같은 `(provider, providerId)`가 이미 가입됨 → registerToken 재사용 차단 |
| `E-AUTH-002` | DUPLICATE_EMAIL (이메일 중복) | 409 | 이메일이 타 계정에서 사용 중 (가입 완료 / 이메일 변경) |
| `E-AUTH-003` | DUPLICATE_NICKNAME (닉네임 중복) | 409 | 닉네임(`users.nickname` 전역 유일)이 타 계정에서 사용 중 (가입 완료 / 온보딩 닉네임 편집) |

### 중복 시나리오별 반환 코드 (웹팀 전달용)

기존에는 세 경우 모두 `DUPLICATE`(이름 기반)로 동일했으나, 이제 코드로 구분된다.

| 시나리오 | 반환 `code` |
|----------|-------------|
| 같은 소셜 신원으로 다시 가입(registerToken 재사용) | `E-AUTH-001` |
| 가입 완료 시 타 계정이 쓰는 이메일 | `E-AUTH-002` |
| `PATCH /me/email`로 타 계정이 쓰는 이메일로 변경 | `E-AUTH-002` |
| 가입 완료 시 타 계정이 쓰는 닉네임 | `E-AUTH-003` |
| 온보딩에서 타 계정이 쓰는 닉네임으로 변경 | `E-AUTH-003` |
| 동시성 경쟁(unique 제약) 폴백: `uq_users_provider_identity` | `E-AUTH-001` |
| 동시성 경쟁(unique 제약) 폴백: email | `E-AUTH-002` |
| 동시성 경쟁(unique 제약) 폴백: `uq_users_nickname` | `E-AUTH-003` |

## E-CMNT-* (커뮤니티)

> `community/error/CommunityErrorCode`. 단일 커뮤니티(공유) — 권한/마스킹은 서버가 뷰어(JWT)+`author_user_id`로 계산한다.

| 코드 | 의미 | HTTP | 발생 지점 |
|------|------|------|-----------|
| `E-CMNT-001` | POST_NOT_FOUND (게시글 없음) | 404 | 미존재/삭제된(soft) 게시글 조회·수정·삭제·좋아요·댓글 |
| `E-CMNT-002` | COMMENT_NOT_FOUND (댓글 없음) | 404 | 미존재/삭제된 댓글 삭제 |
| `E-CMNT-003` | INVALID_CATEGORY (카테고리 오류) | 400 | 허용되지 않은 카테고리(notice/daily/question/knowledge/review/etc 외) |
| `E-CMNT-004` | NOTICE_ADMIN_ONLY (공지 권한) | 403 | 비관리자가 `notice` 작성 시도 |
| `E-CMNT-005` | FORBIDDEN (권한 없음) | 403 | 타인 글 수정(작성자만)·타인 글/댓글 삭제(작성자+관리자 외) |
| `E-CMNT-006` | INVALID_PARENT (부모 댓글 오류) | 400 | 다른 글의 댓글·이미 대댓글인 댓글에 대댓글 시도 |

## E-VRF-* (사업자 인증)

> `verification/error/VerificationErrorCode`. 커뮤니티 접근 게이팅용 사업자 인증. 인증됨 = `status='APPROVED'` 행 존재.

| 코드 | 의미 | HTTP | 발생 지점 |
|------|------|------|-----------|
| `E-VRF-001` | NOT_VERIFIED (미인증) | 403 | 미인증 사용자가 `@RequiresBusinessVerified` 엔드포인트(커뮤니티) 접근 |
| `E-VRF-002` | ALREADY_REQUESTED (중복 신청) | 409 | 이미 `PENDING`/`APPROVED` 인증이 있는데 재신청 |
| `E-VRF-003` | LICENSE_NOT_OWNED (등록증 소유권) | 403 | 등록증 URL 키가 본인 prefix(`business-licenses/{userId}/`)가 아님 |
| `E-VRF-004` | INVALID_LICENSE_TYPE (파일 형식) | 400 | presign 시 허용되지 않은 contentType(jpeg·png·webp·pdf 외) |
