# SPEC-SERVER-011 — 인사이트 API

> status: DOING · deps: 004 · Phase 1 (M2 도메인)

## 목표

인사이트 도메인 REST API. 트렌드/인스타 계정·포스트 공유 읽기, 스크랩 CRUD+메모(polymorphic),
내부 수집 API(Bearer 인증)로 트렌드·포스트 적재 + 신규 트렌드 푸시 브로드캐스트.

## 범위 (In)

- **공유 읽기**(인증 사용자, 테넌트 무관): 트렌드 목록(category/limit/offset)·카테고리별 최신, 인스타 계정(activeOnly), 포스트(accountId/region/sortBy/daysAgo/limit, account 임베드).
- **스크랩**(테넌트 격리): 토글(대상 존재 검증·레이스 안전), 메모(스크랩 후만), 스크랩 맵, 트렌드/포스트 스크랩 목록, 개수.
- **내부 API**(`/internal/**`, Bearer `INTERNAL_API_KEY` 타이밍-세이프): 트렌드 수집(멱등, source_url 중복 스킵, 신규 시 브로드캐스트), 포스트 수집(멱등, shortcode), 인스타 계정 등록/수정/삭제.
- **브로드캐스트**: 전체 활성 push_subscriptions에 푸시(영구실패 토큰 비활성화).
- `/internal/**`는 SecurityConfig permitAll + 컨트롤러에서 `InternalAuthVerifier`로 별도 인증.

## 범위 밖 (Out)

- 사용자 설정/BottomNav(user_preferences) → SPEC-012(원본은 insights.ts에 있으나 설정 도메인).
- "읽음 처리": 스키마에 읽음 상태 테이블/컬럼 없음 → 미구현(스크랩이 저장 메커니즘). 후속 시 스키마 확장 필요.
- 트렌드 카운트/대시보드 위젯 → SPEC-013.

## 인수 기준

1. `./gradlew build test` 통과(key_points/image_urls jsonb validate 포함).
2. 공유 읽기: 시드 인스타 계정(≥15) 조회, 트렌드/포스트 조회(account 임베드).
3. 스크랩: 토글 추가/해제, 미존재 대상 거부, 메모는 스크랩 후만, 맵/목록/개수.
4. 내부 수집 멱등: 트렌드 source_url·포스트 shortcode 중복 스킵(재실행 0건), 같은 배치 중복도 1건.
5. 계정 등록/중복(409)/수정/삭제.
6. 내부 인증: 올바른 Bearer 통과, 틀림/누락/미설정 401(타이밍-세이프).
7. **멀티테넌시 격리**: 다른 user의 스크랩 비노출.

## 설계 메모

- 트렌드/계정/포스트는 공유 테이블(user_id 없음) — 인증만 요구, 테넌트 격리 예외. 스크랩만 격리.
- key_points/image_urls는 Hibernate 네이티브 jsonb. 포스트는 `@ManyToOne` 읽기전용 account(JOIN FETCH).
- 내부 인증은 `MessageDigest.isEqual` 타이밍-세이프. 키 미설정 시 전부 차단(안전 기본값).
- 수집 멱등성은 existsBySourceUrl/existsByShortcode + 배치 내 dedupe. 신규 트렌드만 broadcast.
- 검증은 Zonky 임베디드 PG. (컨텍스트 캐시 공유로 공유 읽기 테스트는 ≥/contains 관대 단언.)
