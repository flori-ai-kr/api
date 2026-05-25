# 컨벤션 ADR (Architecture Decision Records)

이 디렉터리는 hazel-server의 **결정과 그 근거**를 코드와 함께 보존한다. "왜 이렇게 하는가"가 사라지지 않도록, 각 컨벤션은 배경·결정·근거·공식 문서 링크를 한 문서에 담는다.

> 출처 패턴: `socc-assistant-api`의 `docs/conventions/`(결정마다 Overview/Best Practice/Rationale + 공식 참고링크).

## 작성 규칙

- **파일명**: `yy-mm-dd-{슬러그}.md` (예: `2026-05-25-multitenancy-isolation.md`).
- **언어**: 한국어(코드/식별자만 영어).
- **구조**: ① 배경/맥락 → ② 결정(Best Practice) → ③ 근거(Rationale) → ④ 공식 문서/참고 → ⑤ 적용 범위·예외.
- 결정이 바뀌면 새 문서를 추가하고 옛 문서 상단에 `> SUPERSEDED BY {파일}`을 표기한다(삭제하지 않음 — 이력 보존).

## 목록

| 날짜 | 문서 | 요약 |
|------|------|------|
| 2026-05-25 | [멀티테넌시 격리](2026-05-25-multitenancy-isolation.md) | 모든 데이터 쿼리 `user_id` 격리(앱이 유일 방어선) + 자동 가드 테스트 |
| 2026-05-25 | [엔티티 Auditing·업데이트 컨벤션](2026-05-25-entity-auditing-and-update-convention.md) | `BaseEntity` 자동 시각 관리 + 상태 전이는 도메인 메서드 |

> 후속 후보: API 응답·OpenAPI 계약(`@Schema`) 컨벤션, 에러코드/예외 체계 컨벤션.

## 관련 문서

- 패턴·레시피: [`../PATTERNS.md`](../PATTERNS.md)
- 아키텍처(SSOT): [`../DESIGN.md`](../DESIGN.md) · [`../ARCHITECTURE.md`](../ARCHITECTURE.md)
