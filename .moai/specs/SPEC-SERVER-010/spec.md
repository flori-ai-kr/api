# SPEC-SERVER-010 — 사진첩 + 태그 API

> status: DOING · deps: 004 · Phase 1 (M2 도메인)

## 목표

사진 카드(매출 연동) + 태그 도메인 REST API. 카드 CRUD, presigned 업로드 타깃 발급(소유권/메타 검증),
태그 CRUD(삭제 시 카드에서 제거), 커서 페이지네이션·태그/고객 필터.

## 범위 (In)

- **사진 카드**: `PhotoCard`(tags TEXT[], photos jsonb[{url,originalName}] — Hibernate 6 네이티브) + CRUD + 매출(sale_id) 연동 + 매출별 조회.
- **목록**: 커서 페이지네이션(updated_at desc, page 8) + tag 포함 필터 + customerId(sales 조인) 필터. 단일 네이티브 쿼리(NULL 파라미터 CAST 처리).
- **업로드 타깃**: `POST /photo-cards/{id}/upload-targets` — SPEC-004 `S3PresignService`로 presigned PUT 발급. 소유권 확인, 카드당 최대 10장, 이미지 메타(type/size) 검증, 키 생성(`photo-cards/{id}/{uuid}-{name}`).
- **사진 조작**: 순서 변경, 1장 삭제. 카드 삭제 시 정리 대상 사진 목록 반환.
- **태그**: `PhotoTag` CRUD(이름 정렬, (name,user_id) 중복 409, 색상 랜덤). 삭제 시 카드들의 tags 배열에서 제거(`array_remove`).
- 모든 쿼리 `TenantContext.currentUserId()` 격리(HARD).

## 범위 밖 (Out)

- S3 객체 실제 삭제(카드/사진 삭제 시) — v1은 정리 대상 URL 반환만(후속). presign 발급은 구현.
- 다운로드 서명 URL, 일괄 다운로드 (후속).

## 인수 기준

1. `./gradlew build test` 통과(tags TEXT[]/photos jsonb validate 포함).
2. 카드 CRUD + 커서 페이지네이션 + 태그/고객 필터.
3. 업로드 타깃: 소유권/최대 10장/이미지 메타 검증. 성공 시 presigned PUT + 파일 URL + originalName.
4. 사진 순서 변경/1장 삭제, 사진 11장 이상 거부.
5. 태그 CRUD, 중복 409, 삭제 시 카드 tags에서 제거.
6. **멀티테넌시 격리**: 다른 user의 카드/태그 조회·삭제 차단.

## 설계 메모

- tags/photos는 Hibernate 네이티브 `@JdbcTypeCode(ARRAY/JSON)`(validate 친화적, SPEC-006과 동일).
- 목록은 네이티브 쿼리 1개로 커서+태그(`= ANY`)+고객(sales 조인) 필터를 NULL CAST 패턴으로 처리.
- 업로드 타깃 성공경로 검증은 정적 자격증명 presigner로(로컬 서명). 소유권/장수/메타 실패경로는 presign 이전에 차단.
- 태그 삭제 cascade는 `array_remove` @Modifying 네이티브 쿼리.
- 검증은 Zonky 임베디드 PG.
