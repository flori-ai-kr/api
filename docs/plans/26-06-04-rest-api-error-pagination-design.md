# REST API 설계 일관성 — 에러 표준(RFC 9457) · 페이지네이션 전략

> 상태: **설계 분석(합의 대기)** — 코드 변경 없음. server-C 축은 클라이언트(web·mobile) 응답 계약을
> 깰 위험이 커서, 이 문서로 현황·옵션·블래스트 반경을 먼저 정리하고 방향을 합의한 뒤에만 적용한다.
> 작성: 2026-06-04.

---

## 0. 요약 (TL;DR)

- **에러 포맷**: 현재 `{code, message}` 2필드 + `E-{DOMAIN}-{NNN}` 코드 체계. web·mobile이 `$.code`로
  분기하므로 RFC 9457(`application/problem+json`)로의 **본문 구조 변경은 파괴적**. → **권장: 당장 전환하지 않음.**
  필요 시 기존 2필드를 유지한 채 RFC 9457 필드를 **가산(additive)** 하는 방식만 검토.
- **페이지네이션**: offset(매출·커뮤니티) / keyset(사진) / 전체로드(고객) / Pageable+total(admin) **4종 혼재**.
  각자 합리적 이유가 있으나 기준이 문서화돼 있지 않다. → **권장: "전략 선택 기준"을 컨벤션으로 문서화**하고,
  신규/무한스크롤 목록은 **keyset 우선**. 기존 엔드포인트의 엔벨로프 변경은 보류(계약 호환).

---

## 1. 에러 응답 포맷

### 1.1 현재 (SSOT: `common/error/`)

```kotlin
// common/error/ErrorResponse.kt
data class ErrorResponse(val code: String, val message: String)
```

```json
{ "code": "E-CMN-001", "message": "입력값이 올바르지 않습니다" }
```

- `GlobalExceptionHandler`가 `AppException`을 잡아 `ResponseEntity<ErrorResponse>`(상태=errorCode.status)로 반환.
- 데이터 엔벨로프 래핑 없음(성공도 DTO 직접 반환). 내부 스택/쿼리는 비노출 + Discord에만 상세.
- 코드 체계 `E-{DOMAIN}-{NNN}` — 클라이언트 분기 키(공표 후 불변 계약):
  - 공통 `E-CMN-001..006, 999`(VALIDATION/UNAUTHORIZED/INVALID_TOKEN/FORBIDDEN/NOT_FOUND/CONFLICT/INTERNAL)
  - 도메인별 `E-AUTH-*`, `E-ADM-*`, `E-VRF-*`, `E-CMNT-*` 등 (상세: `docs/ERROR_CODES.md`)

### 1.2 RFC 9457 Problem Details (공식 표준)

Spring은 `spring.mvc.problemdetails.enabled=true`로 `ProblemDetail`(`application/problem+json`)을 네이티브 지원.
표준 필드: `type`(URI), `title`, `status`, `detail`, `instance` + 확장 멤버.

```json
{
  "type": "https://api.flori.kr/errors/validation",
  "title": "Validation Failed",
  "status": 400,
  "detail": "입력값이 올바르지 않습니다",
  "instance": "/sales",
  "code": "E-CMN-001"
}
```

### 1.3 블래스트 반경(클라이언트 호환)

| 변경 | 영향 |
|------|------|
| 본문 구조를 ProblemDetail로 **교체** | web·mobile의 `$.code`/`$.message` 파싱이 전부 깨짐. orval 에러 타입도 재생성 필요. **파괴적** |
| `Content-Type`을 `application/problem+json`으로 변경 | 클라 인터셉터가 `application/json`만 처리하면 에러 핸들링 우회 |
| 기존 2필드 유지 + RFC 필드 **가산** | 비파괴적. 단 두 포맷 공존으로 표준 이점(상호운용성)은 부분적 |

### 1.4 권장

1. **현행 유지가 기본.** `E-{DOMAIN}-{NNN}` + `{code,message}`는 이미 web·mobile·문서가 의존하는 안정 계약이고,
   Flori는 단일 조직이 클라이언트를 함께 소유하므로 RFC 9457의 핵심 가치(외부 상호운용성)가 약하다.
2. 굳이 도입한다면 **가산 방식**(기존 2필드 보존 + `status`/`type` 추가)으로, 클라가 점진 마이그레이션할 수 있게.
   `Content-Type`은 당분간 `application/json` 유지.
3. **전환 트리거**: (a) 외부 파트너에 API 공개, (b) 표준 에러 기반 게이트웨이/모니터링 도입,
   (c) 멀티버전(`/v2`) 도입 시 — 셋 중 하나가 생기면 재평가.

---

## 2. 페이지네이션 전략

### 2.1 현황 — 4종 혼재

| 방식 | 사용처 | 요청 파라미터 | 응답 엔벨로프 | total | 비고 |
|------|--------|---------------|----------------|:----:|------|
| **offset** | 매출(`SaleController.list`), 커뮤니티(`listPosts`) | `offset`,`limit`(+필터) | `{ sales/posts:[], hasMore }` | ✕ | `PageRequest.of(offset/limit, limit)` |
| **keyset(cursor)** | 사진(`PhotoCardController.list`) | `cursor`(updatedAt ISO), 필터 | `{ cards:[], nextCursor, hasMore }` | ✕ | 네이티브 `WHERE updated_at < :cursor`. 무한스크롤 정석 |
| **full-load** | 고객(`CustomerController.list`) | 없음 | `List<CustomerResponse>`(배열 직접) | n/a | 의도적 전체 로드(구매통계 실시간 집계 + 클라 정렬) |
| **Pageable+total** | admin 유저(`AdminUserController.list`) | `page`,`size`,`query` | `{ rows:[], page, size, total }` | ○ | JdbcTemplate `COUNT(*)` + `LIMIT/OFFSET` |

추가로 `/customers/{id}/sales`는 offset 변형(`page`,`size`), admin 구독/인증/통계는 Pageable·전용 DTO.

### 2.2 best-practice (공식)

- **무한스크롤/대량은 keyset**(Spring Data `Scrolling`/`ScrollPosition.keyset()`): 높은 offset은 O(offset)이고
  동시 삽입 시 행이 밀려 중복/누락. keyset은 안정·효율.
- **total이 필요한 화면(페이지 번호 UI)** 만 `COUNT` 비용을 지불. 무한스크롤은 `hasMore`로 충분.
- 컬렉션 fetch join + 페이징 동시 금지(HHH90003004, 인메모리 페이징 OOM).

### 2.3 권장 — "기준 문서화" 우선 (엔벨로프 변경은 보류)

현재 4종은 **각각 합리적**이다(고객=전체로드 의도, admin=페이지번호 UI라 total 필요, 사진=무한스크롤 keyset).
문제는 "언제 무엇을 쓰는가"가 명문화돼 있지 않은 것. → 컨벤션으로 다음 기준만 고정한다:

| 화면 성격 | 권장 방식 | 응답 |
|-----------|-----------|------|
| 무한스크롤(피드형) | **keyset(cursor)** | `{ items:[], nextCursor, hasMore }` |
| 페이지 번호 UI / 운영 콘솔 | Pageable + `COUNT` | `{ rows:[], page, size, total }` |
| 소량·전량 표시(드롭다운 등) | full-load | 배열 직접 |

- **신규 무한스크롤 목록은 keyset 우선.** offset 신규 추가 지양.
- **기존 엔드포인트(매출·커뮤니티 offset)의 엔벨로프 변경은 보류** — `{...:[], hasMore}` 계약을 web·mobile이
  의존. 전환한다면 keyset로 갈 때 `nextCursor` 가산 + 한동안 offset 병행 후 폐기(2단계).
- 엔벨로프 키 네이밍(`sales`/`posts`/`cards`/`rows`)이 제각각 — 신규는 `items`로 통일 권장(기존은 유지).

### 2.4 전환 트리거

- 매출/커뮤니티 데이터가 커져 offset 깊은 페이지 지연이 체감되면 → 해당 목록을 keyset로 (가산 후 폐기 2단계).
- 페이지 번호 UI가 정말 필요한 신규 화면 → Pageable+total 명시 채택.

---

## 3. 결론 (합의 대기 항목)

1. **에러 RFC 9457**: 당장 전환 ✕. 외부 공개/멀티버전 시점에 **가산 방식**으로 재평가. — *결정 필요*
2. **페이지네이션**: 4종 현행 유지하되 **선택 기준을 컨벤션으로 문서화**, 신규 무한스크롤은 keyset.
   기존 엔벨로프 변경은 2단계(가산→폐기)로만. — *결정 필요*
3. 위 두 결정이 "진행"으로 확정되면, 컨벤션 문서(`docs/conventions/`) 추가 + 신규 엔드포인트부터 적용한다
   (기존 계약 일괄 변경은 별도 마이그레이션 계획으로 분리).
