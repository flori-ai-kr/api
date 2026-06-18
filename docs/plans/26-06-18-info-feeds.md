# 정보 피드(인사이트) 부활 — 경매 시세·지원사업·트렌드/뉴스

작성: 2026-06-18 · 브랜치: `feat/info-feeds` (web·api 워크트리)

## 범위 (확정)

- 정보 탭 3개를 **표시 전용(display-only)** 으로 부활/신규. AI 분석·추천은 **후속**(반응 후 고도화).
- 탭: **경매 시세 · 지원사업 · 트렌드·뉴스**. 팔로우(인스타그램) 기능은 **전부 제거**.
- 스크랩(사장님별 북마크)은 부활.
- UI 시안: `docs/_tmp/info-tabs-mockup.html` (메인=언더라인 탭=statistics / 필터=pill 칩=trends).

## DB (4테이블 · FK 없음 간접참조 · 공유 3 + 개인 1)

> **경매 시세는 단일 시장(aT 양재)이다.** 라이브 API 테스트로 확인: 실제 aT 화훼유통정보 f001 응답에는
> 시장/법인(corp_code) 필드가 없고, 포털 카탈로그에도 함수가 하나뿐이다. 따라서 다중 시장 매핑 테이블
> (`flower_markets`)은 폐기하고, 응답 텍스트(절화/관엽/난/춘란)를 그대로 적재한다.

| 테이블 | 신규/부활 | 성격 | 비고 |
|---|---|---|---|
| `trend_articles` | 부활 | 공유 | 뉴스·트렌드·시즌 (category: flower/inspiration/industry 사용) |
| `flower_auction_prices` | 신규 | 공유 | f001 일별 경락가(단일 시장). sale_date/flower_gubn/pum_name/good_name/lv_nm + 금액/수량. 등락률은 **파생 계산** |
| `support_programs` | 신규 | 공유 | 소진공·기업마당·K-Startup |
| `insight_scraps` | 부활 | **개인(user_id)** | target_type: `trend`\|`grant`, FK 없는 간접참조 |

- DDL: `migration/26-06-18-revive-info-feeds.sql`(+rollback), `all-tables-ddl.sql` 반영 완료. `flower_markets` 와 seed 스캐폴드는 제거.
- `flower_auction_prices` UNIQUE (sale_date, flower_gubn, pum_name, good_name, lv_nm), `idx_fap_item_date(flower_gubn, pum_name, good_name, lv_nm, sale_date DESC)`.

## ⚠️ 외부 의존성 (사용자 발급 — 적재 cron 전제, 스키마/읽기/UI는 무관)

- **aT 화훼유통정보 OpenAPI `f001` serviceKey** — flower.at.or.kr (자동승인·무료, 1,000건/일). 단일 시장(aT 양재) 일별 경락가 적재의 전제. 이용허락범위 = "제작자 표시"(응답에 출처 표기 필수).
- **지원사업** 키 — 기업마당/공공데이터포털(소진공·K-Startup).
- **뉴스 소스** 확정 — RSS/공개. 저작권상 헤드라인+요약+원문링크만.

## api 레이어 (`kr.ai.flori.insights` 신설)

- entity/repository/service/controller (도메인 레이어 패턴, DTO 경계). 읽기 우선:
  - `GET /insights/trends?category&offset&limit`
  - 경매(단일 시장 aT 양재):
    - `GET /insights/auction/categories` — 정적 4종(1=절화/2=관엽/3=난/4=춘란).
    - `GET /insights/auction/dates?gubn` — 데이터가 있는 정산일자 최신순 distinct 목록(상한 ~60). date picker용. 응답 `["2026-06-18","2026-06-17",…]`.
    - `GET /insights/auction/summary?date&gubn` — **품목(pum_name) 단위 요약**. 응답 `{date, source, items:[{pumName, repAvg, repChangeRate, variantCount}]}`. items 는 거래량 많은 순.
    - `GET /insights/auction/prices?date&gubn&item` — **드릴다운**(품목 → 품종·등급 행). 응답 `{date, source, prices:[…행…]}`. `item` 은 pum_name 필터.
  - `GET /insights/grants?category&offset&limit`
  - 스크랩(개인): `GET /insights/scraps`, `POST /insights/scraps`, `DELETE /insights/scraps`
- 시세 행 응답: `{date, source:"화훼유통정보(aT)", prices:[{flowerGubn,pumName,goodName,lvNm,avgAmt,maxAmt,minAmt,totQty,totAmt,prevAvgAmt,changeRate}]}`.
- 등락률(`changeRate`)은 응답 DTO에서 직전 정산일자 평균가 대비 **파생 계산**(네이티브 LAG 윈도, `idx_fap_item_date` 활용). `source` 는 이용허락범위(제작자 표시) 준수.

### 경매 요약(summary) — 표시 규칙 (확정)

- **기본 기준일 = 완전한(complete) 최신 정산일.** `date` 미지정 시: 최근 14일 일별 행 수의 최댓값(`max daily count`)을 구해, 그 **0.5배 이상**인 정산일 중 가장 최근 날짜를 채택한다(부분/미완 적재된 최신일 — 예: ~98행짜리 — 스킵). 자격 일자가 없으면 가장 최근 정산일로 폴백. `summary`·`prices` 모두 동일 로직(일관성). SQL(`COMPLETE_DATE_SQL`)에서 계산.
- **repAvg = 거래량 가중평균** = `round(sum(tot_amt)/sum(tot_qty))`. 거래량 0/없으면 null.
- **repChangeRate = 등락 방식 A(매칭 품종·등급 중앙값).** 품목의 각 (good_name, lv_nm) 행의 직전 정산일 대비 등락률(LAG 파생) 중 **양일 모두 존재(non-null)** 하는 변형만 추려 `percentile_cont(0.5)` **중앙값**을 SQL에서 계산. 매칭 변형 없으면 null. (가중평균 mix-shift 노이즈 회피.)
- **variantCount** = 그 품목·그날의 (good_name, lv_nm) 행 수.
- SQL-heavy 로직(중앙값·완전일·distinct)은 컨벤션대로 `FlowerAuctionPriceQueryRepository`(네이티브) 레이어에 둔다.

## 적재 cron (@Scheduled, KST — 키 발급 후)

- 시세: `FlowerAuctionIngestService` — 매일 KST(기본 06:30) 최근 3일(backfill, 정산 지연/누락일 커버)을 4개 flowerGubn 전부 페이징 수집 → `flower_auction_prices` upsert(`ON CONFLICT (sale_date,flower_gubn,pum_name,good_name,lv_nm) DO UPDATE` — 정산 정정 반영). per-day/per-gubn 격리(한 건 실패가 나머지 무중단), 빈 날 스킵.
- HTTP 클라이언트(`FlowerApiClient`)는 base-url/serviceKey 를 `flori.flower-api.*`(`${ENV}`)에서 주입. serviceKey 미설정이면 cron no-op(경고 로그) — 키 없는 dev 부팅 가능.
- 지원사업/뉴스: 소스 어댑터별 수집 → upsert. 뉴스 Haiku 요약은 후속(지금은 헤드라인+링크).

## web (`web-info-feeds`)

- insights redirect 해제 + 라우트 복구, **follows 전체 제거**(routes/actions/types/components/타입).
- 화면: 메인 **언더라인 탭** 3개 + 필터 **pill 칩**. 시세(KPI 2 + 카드 내 divide-y 행), 지원사업(카드+D-day), 트렌드·뉴스(카드 행+스크랩).
- `insight_scraps` target_type `post` 제거 → `trend`/`grant`. bottom-nav '정보' 정합.

## 빌드 순서

1. ✅ DB(DDL) — migration/rollback/all-tables-ddl/seed scaffold
2. api 엔티티 + 읽기 API (+ 스크랩)
3. web UI (팔로우 제거 + 3탭 + 언더라인/칩)
4. 적재 cron (serviceKey/소스키 발급 후)
