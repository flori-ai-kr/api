# SPEC-SERVER-012 — 설정 API

> status: DOING · deps: 004 · Phase 1 (M2 도메인)

## 목표

설정 도메인 REST API. 카드사(수수료/입금일), 매출/지출 설정(카테고리·결제방식), 사용자 설정(하단바 JSONB),
푸시 구독 등록/해지. 대부분 가입 시 시드된 테이블(SPEC-003)을 CRUD로 노출한다.

## 범위 (In)

- **value/label 설정 4종**(매출 카테고리/결제방식, 지출 카테고리/결제방식): `@MappedSuperclass LabelSetting` + 4 엔티티 + 제네릭 추상 서비스로 DRY 구현. list/add(슬러그 생성)/update/delete, (value,user_id) 중복 409.
- **카드사**: list(활성)/create/update(fee_rate·deposit_days)/소프트 삭제(is_active=false), (name,user_id) 중복 409.
- **사용자 설정**: `user_preferences`(PK=user_id) bottom_nav_items(jsonb 문자열 배열) 조회(기본값)·변경(upsert, 4~6개).
- **푸시 구독**: `push_subscriptions` endpoint(FCM 토큰) upsert 등록/해지(is_active)/상태.
- 모든 쿼리 `TenantContext.currentUserId()` 격리(HARD).

## 범위 밖 (Out)

- product_categories(원본 별도 테이블) — 우리 스키마엔 없음. 매출 카테고리는 sale_categories 사용.
- 일괄 저장(saveAllSettings) — 개별 CRUD로 대체.

## 인수 기준

1. `./gradlew build test` 통과(bottom_nav_items jsonb validate 포함).
2. value/label 설정: 시드(매출 카테고리 11) + 추가/수정/삭제, 중복 value 409, 슬러그 자동 생성.
3. 카드사: 시드(9) + 등록/수정/소프트삭제(활성 목록 제외), 중복 409.
4. 사용자 설정: 기본값 반환 후 변경 저장(upsert).
5. 푸시 구독: 등록→상태 true, 해지→false. endpoint upsert.
6. **멀티테넌시 격리**: 다른 user의 설정 비노출.

## 설계 메모

- value/label 4종은 동일 구조 → `@MappedSuperclass` + `@NoRepositoryBean` 제네릭 베이스 리포지토리 + 추상 `LabelSettingService<T>`로 중복 제거.
- bottom_nav_items는 List<String> jsonb(네이티브 JSON) — photo_cards.tags가 Array<String>로 분리되어 List<String>은 전역 JSON으로만 해석(타입 충돌 없음).
- 카드사/카테고리/결제방식은 가입 시 시드되어 있으므로 본 SPEC은 노출·변경만.
- 검증은 Zonky 임베디드 PG(가입 시드 카운트로 검증).
