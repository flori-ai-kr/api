# SPEC-SERVER-020 — 에러리포팅 PII 마스킹 + truncate (B4)

## 목표
Discord 운영오류 리포팅에서 **PII·시크릿이 새지 않도록** 마스킹 범위를 메시지/액션 필드까지 넓히고, 전화번호 마스킹을 추가한다. 출처: `onetime/batch`의 에러 메시지 마스킹 + 길이 제한 패턴. CLAUDE.md "에러 응답에 내부 디테일 노출 금지" 강화.

## 배경(기존 갭)
- 기존 `sanitizeStack`은 **스택 트레이스**의 경로/이메일/토큰/비밀번호/키만 마스킹.
- `buildPayload`의 **오류 메시지 필드는 truncate만**(마스킹 없음), **액션 필드는 truncate도 마스킹도 없음** → 메시지에 담긴 PII가 Discord로 유출 가능.
- **전화번호 마스킹 부재** — flori은 고객 전화번호를 다루며, 예: `duplicate (phone, user_id)=(010-1234-5678, …)` 같은 DB 에러 메시지가 그대로 전송될 수 있음.

## 구현
- `DiscordReporting.kt`: 공통 `maskSensitive(text)` 추출(경로/이메일/**전화번호**/토큰/비밀번호/키).
  - `sanitizeStack` = `maskSensitive` + 줄 수 제한.
  - `sanitizeMessage` = `maskSensitive`(줄 수 제한 없음) — 단문 필드용.
  - 전화번호 정규식 `\d{2,3}-\d{3,4}-\d{4}` → `[PHONE]`(휴대폰·유선 dashed 형태). 날짜(`2026-05-25`)는 두 번째 그룹이 3자리 미만이라 미매칭.
- `DiscordErrorReporter.buildPayload`: 메시지·액션 필드에 `sanitizeMessage` + `truncate(MAX_FIELD_LENGTH)` 적용.

## 인수기준
- [x] 메시지/액션 필드 PII 마스킹(이메일·전화번호) + truncate
- [x] 전화번호 마스킹(스택·메시지 공통)
- [x] 기존 스택 마스킹 동작 보존
- [x] 테스트 추가(메시지 이메일·전화 마스킹, 스택 전화 마스킹)
- [x] `./gradlew build test` 그린 — 170 테스트(+2, 0 실패/0 스킵)

## 비고
- 마스킹은 best-effort 정규식. 새 PII 유형(주소 등)이 생기면 `maskSensitive`에 규칙 추가.
- dedup 키는 내부 메모리에만 쓰이고 외부 전송하지 않으므로 마스킹 대상 아님.
