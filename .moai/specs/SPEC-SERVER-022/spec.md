# SPEC-SERVER-022 — RestDocs 기반 테스트 검증 API 문서 + 커버리지 80% (E2)

## 목표
springdoc **어노테이션 기반** Swagger를, **테스트가 보증하는 OpenAPI 3 문서**로 전환한다.
Spring REST Docs + ePages `restdocs-api-spec`으로 기존 통합테스트에서 OpenAPI 스펙을 생성해 Swagger UI로 서빙하고, JaCoCo로 **라인 커버리지 80% 게이트**를 건다. (E1=SPEC-018의 후속)

## 배경
- 출처: `onetime/backend`의 검증된 RestDocs+Swagger 셋업 — `com.epages.restdocs-api-spec` 0.19.2, `openapi3` → `static/docs/open-api-3.0.1.json`, springdoc을 뷰어로 유지. (사용자 지시: "onetime처럼")
- 기존 flori: springdoc **어노테이션 기반**(`@Operation` 22파일, `@Schema` 3파일) — 코드와 **드리프트 가능**, 테스트가 문서를 보증하지 않음. 22개 컨트롤러 중 직접 MockMvc 테스트는 4개(Sale·Customer·Subscription·Health)뿐.
- onetime과의 차이: onetime은 서비스 `@MockBean` 컨트롤러 슬라이스(Java). **flori는 풀 통합테스트(실서비스 + Zonky 임베디드 PG)가 이미 있어 그 위에 RestDocs를 얹는다** — 실 end-to-end 문서 + 로직 커버리지를 한 계층에서 동시 확보(사용자 결정).

## 설계 결정
1. **라이브러리**: `com.epages.restdocs-api-spec` **0.19.5** + `spring-restdocs-mockmvc` (Spring REST Docs 3.x). onetime의 0.19.2 대비 최신 — Spring Boot 3.5 호환 안전. keecon 포크(타입추론)는 2024-10로 오래돼 미채택.
2. **문서 SSOT = 테스트**. springdoc은 **뷰어로만 유지**:
   - `springdoc.api-docs.enabled: false` (런타임 어노테이션 스캔 끔)
   - `springdoc.swagger-ui.url: /docs/open-api-3.0.1.json` → `/swagger-ui.html`이 **테스트 생성 정적 스펙** 표시
   - onetime의 `hidetake swagger.generator`는 flori엔 **불필요**(springdoc UI 재사용으로 간소화)
3. **테스트 스타일**: 기존 통합테스트(`@SpringBootTest`+`@AutoConfigureMockMvc`+Zonky)에 `@AutoConfigureRestDocs` 추가 + `document(resource(...))` 연결. 공용 추상 베이스 `RestDocsSupport`.
4. **중복 제거**: 문서 출처가 테스트가 되므로 컨트롤러 `@Operation`/`@Schema`/`@Tag` 제거(풀 도입, 코드 정리). JWT bearer SecurityScheme는 OpenAPI 보안 표기를 위해 유지/이관.
5. **커버리지**: JaCoCo **line 80% 전체** 게이트(`jacocoTestCoverageVerification`), `check`가 의존, CI 반영. 제외: `*Application`, `common/config/**`, `**/dto/**`, 생성물.

## 범위
**In**
- `build.gradle.kts`(Kotlin DSL): `restdocs-api-spec`+`spring-restdocs-mockmvc`+`jacoco` 배선, `openapi3 {}` 블록, 스니펫 디렉토리·태스크
- `application.yml`: springdoc 뷰어 설정(정적 스펙)
- `RestDocsSupport` 베이스 + 22개 컨트롤러 주요 엔드포인트 RestDocs 문서화(요청/응답 필드 디스크립터)
- 컨트롤러 springdoc 문서 어노테이션 제거
- JaCoCo 게이트 + 80% 미달분 테스트 보강
- CI(`ci.yml`) jacoco 리포트/검증 반영
- 문서: README/PATTERNS/ROADMAP/HANDOFF 갱신

**Out**
- `hidetake swagger.generator`(불필요), asciidoctor HTML 문서(Swagger로 충분)
- 실제 배포/인프라

## 인수기준
- [ ] `./gradlew build test` 그린 — RestDocs 스니펫 + `open-api-3.0.1.json` 생성
- [ ] `/swagger-ui.html`이 테스트 생성 스펙 표시 + JWT Authorize 동작
- [ ] 22개 컨트롤러 주요 엔드포인트 RestDocs 문서화(요청/응답 필드 기술)
- [ ] springdoc 런타임 스캔 비활성(`api-docs.enabled=false`) + 컨트롤러 문서 어노테이션 중복 제거
- [ ] JaCoCo line 커버리지 **≥ 80%**(제외목록 적용), `jacocoTestCoverageVerification`이 build 게이트
- [ ] CI에 jacoco 리포트/검증 반영
- [ ] 동작 변경 0(문서·테스트·빌드 인프라만)

## 단계
1. 빌드 배선(`restdocs-api-spec`/`jacoco`/`openapi3`) + springdoc 뷰어 설정 → 빌드 그린
2. `RestDocsSupport` 베이스 + **auth PoC 1개** → `open-api-3.0.1.json` 생성·swagger 확인(라이브러리/배선 검증)
3. 도메인별 점진 RestDocs 적용(sales → customers → reservations → … → subscriptions) + 어노테이션 제거
4. JaCoCo 베이스라인 측정 → 80% 미달분 테스트 보강
5. CI 반영, 문서/ROADMAP/HANDOFF 갱신 → **dev로 PR**

## 비고 / 함정
- **Kotlin MockMvc DSL + RestDocs**: 자바의 `.andDo(document())`와 달리 `andDo { handle(MockMvcRestDocumentationWrapper.document("identifier", resource(...))) }` 형태로 연결한다.
- ePages `resource(ResourceSnippetParameters)`로 태그·요약·request/response 필드를 한 번에 기술 → OpenAPI 풍부화.
- Zonky 통합테스트라 문서 생성에 실 DB가 필요 — CI에서도 동일(별도 서비스 컨테이너 불필요).
- **커버리지 80% 핵심 지렛대 = 컨트롤러 RestDocs 테스트 신규분**(현재 컨트롤러 직접테스트 4/22). 서비스 로직은 기존 통합테스트가 이미 상당 부분 커버.
- 작업 브랜치: `feature/SPEC-SERVER-022-restdocs`(off dev) → 완료 시 dev로 PR(auto-assign/PR템플릿).
