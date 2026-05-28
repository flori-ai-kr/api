# DB 스키마 (DDL 직접 관리)

Flori는 Flyway 같은 런타임 마이그레이션 도구를 쓰지 않고 **DDL을 직접 관리**한다. 스키마의 단일 정본(SSOT)은 이 디렉터리이며, 애플리케이션은 부팅 시 `ddl-auto: validate`로 엔티티와 실제 DB의 정합성만 검증한다(스키마를 생성/변경하지 않음).

## 구성

```
docs/sql/
├── all-tables-ddl.sql      # 현재 전체 스키마 스냅샷 (SSOT)
├── seed.sql                # 공유 시드 (instagram_accounts 등, 멱등)
└── migration/              # 이후 증분 변경 스크립트 + 롤백
    ├── yy-mm-dd-{슬러그}.sql
    └── yy-mm-dd-{슬러그}-rollback.sql
```

## 신규 DB 구축 (로컬 / RDS)

```bash
psql "$DB_URL" -f docs/sql/all-tables-ddl.sql
psql "$DB_URL" -f docs/sql/seed.sql
```

> 로컬은 `docker-compose up -d`로 `flori-pg`를 띄운 뒤 위 명령을 적용한다. 더 이상 부팅 시 자동 적용되지 않는다.

## 스키마 변경 절차

1. `migration/`에 증분 스크립트 작성 — `START TRANSACTION; ... COMMIT;`로 감싸고, 같은 이름의 `-rollback.sql`을 함께 만든다.
2. 대상 DB(로컬 → dev → prod)에 **수동 적용**한다.
3. **`all-tables-ddl.sql`에도 같은 변경을 반영**한다(스냅샷이 항상 현재 상태를 나타내야 함).
4. 엔티티도 함께 수정하고 `./gradlew build test`로 검증한다. 테스트는 아래 방식으로 이 파일들을 그대로 적용하므로 별도 복사가 필요 없다.

## 테스트와의 동기화 (단일 정본)

테스트는 Zonky 임베디드 PostgreSQL에 스키마를 올려야 한다. DDL을 두 곳에서 관리하지 않도록, **테스트 전용 `test` 프로필**(`src/test/resources/application-test.yml`)이 `spring.sql.init`로 이 디렉터리의 파일을 직접 참조한다:

```yaml
spring:
  sql:
    init:
      mode: always
      schema-locations: file:docs/sql/all-tables-ddl.sql
      data-locations: file:docs/sql/seed.sql
      # plpgsql 트리거 함수의 $$ 본문 세미콜론이 statement 구분자로 오해되지 않도록 파일 전체를 한 statement로 전달
      separator: "^^^ END OF SCRIPT ^^^"
```

`test` 프로필은 `build.gradle.kts`의 테스트 작업에서 `spring.profiles.active=local,test`로 활성화된다. SQL init이 임베디드 DB에 적용한 뒤 `ddl-auto: validate`가 검증한다. 즉 **DDL 정본은 항상 `docs/sql/`** 한 곳이며, 복사본을 따로 두지 않는다.
