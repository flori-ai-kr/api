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
4. 엔티티도 함께 수정하고 `./gradlew build test`로 검증한다. 테스트는 `all-tables-ddl.sql` + `seed.sql`을 자동 복사해 임베디드 PostgreSQL에 적용하므로(아래), 별도 작업이 필요 없다.

## 테스트와의 동기화 (단일 정본)

테스트는 Zonky 임베디드 PostgreSQL에 스키마를 올려야 한다. DDL을 두 곳에서 관리하지 않도록, Gradle `processTestResources`가 빌드 시 이 파일들을 테스트 리소스로 자동 복사한다(`build.gradle.kts`):

- `all-tables-ddl.sql` → `schema.sql`
- `seed.sql` → `data.sql`

Spring Boot SQL init(`spring.sql.init`)이 이를 임베디드 DB에 적용한 뒤 `ddl-auto: validate`가 검증한다. 즉 **DDL 정본은 항상 `docs/sql/`** 한 곳이다.
