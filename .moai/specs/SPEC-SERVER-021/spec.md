# SPEC-SERVER-021 — 컨벤션 ADR 문서 체계 (DOC1)

## 목표
결정의 **근거(왜)** 가 코드와 함께 보존되도록 `docs/conventions/` ADR 디렉터리를 신설한다. 출처: `socc-assistant-api`의 `docs/conventions/`(결정마다 Overview/Best Practice/Rationale + 공식 참고링크).

## 배경
- 기존 flori은 `docs/PATTERNS.md` 단일 파일로 "어떻게(how)"는 잘 설명하나, 개별 결정의 "왜(why)"·대안·근거가 흩어져 있었다.
- 무인 loop로 진행되는 repo 특성상, 미래 세션이 결정 배경을 빠르게 파악할 수 있어야 한다.

## 구현
- `docs/conventions/README.md`: 인덱스 + 작성 규칙(파일명 `yy-mm-dd-{슬러그}.md`, 구조 배경/결정/근거/공식링크/적용범위, 변경 시 `SUPERSEDED BY`로 이력 보존, 한국어).
- ADR 2건(이번 라운드에 정착시킨 핵심 컨벤션):
  - `2026-05-25-multitenancy-isolation.md` — `user_id` 격리 원칙 + 리포지토리 컨벤션 + 자동 가드(SPEC-016) + 의도적 전역 화이트리스트.
  - `2026-05-25-entity-auditing-and-update-convention.md` — `BaseEntity` 자동 시각 관리 + 상태 전이 도메인 메서드(SPEC-017).
- `docs/PATTERNS.md` 인트로에서 conventions 링크.

## 인수기준
- [x] `docs/conventions/` 디렉터리 + README(인덱스·작성규칙)
- [x] 핵심 ADR 2건(근거 + 공식 문서 링크 포함)
- [x] PATTERNS.md ↔ conventions 상호 링크
- [x] `./gradlew build test` 그린 유지(문서만 변경, 170 테스트)

## 비고
- 후속 ADR 후보: API 응답·OpenAPI 계약 컨벤션, 에러코드/예외 체계 컨벤션. 새 결정 시 같은 형식으로 추가.
- 문서만 변경 — 코드/동작 변화 없음.
