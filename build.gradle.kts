plugins {
    val kotlinVersion = "2.1.0"
    id("org.springframework.boot") version "3.5.14"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    kotlin("plugin.jpa") version kotlinVersion
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
    id("com.epages.restdocs-api-spec") version "0.19.2"
    jacoco
}

group = "kr.ai.flori"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val springdocVersion = "2.8.17"
val hypersistenceVersion = "3.9.0"
val embeddedDbTestVersion = "2.5.1"
val jjwtVersion = "0.12.6"
val awsSdkVersion = "2.29.20"
val firebaseAdminVersion = "9.4.1"
val logstashEncoderVersion = "8.1"
val webPushVersion = "5.1.1"
val bouncyCastleVersion = "1.78.1"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
    // jsonb / 배열 컬럼의 JPA 매핑 (도메인 SPEC에서 사용)
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:$hypersistenceVersion")

    // 자체 JWT
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    runtimeOnly("org.postgresql:postgresql")

    // S3 presigned URL 발급
    implementation(platform("software.amazon.awssdk:bom:$awsSdkVersion"))
    implementation("software.amazon.awssdk:s3")
    // FCM 푸시(모바일)
    implementation("com.google.firebase:firebase-admin:$firebaseAdminVersion")
    // Web Push(VAPID) — 브라우저(PWA) 푸시. BouncyCastle로 VAPID 서명.
    implementation("nl.martijndwars:web-push:$webPushVersion")
    implementation("org.bouncycastle:bcprov-jdk18on:$bouncyCastleVersion")
    // 운영 프로필 JSON 구조화 로깅(LogstashEncoder + StructuredArguments.kv) — logback 1.5.x / Jackson 2.x 호환 8.x 라인
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Docker 없이 실제 PostgreSQL을 띄워 스키마(docs/sql)·리포지토리를 검증 (CI·로컬 동일)
    testImplementation("io.zonky.test:embedded-database-spring-test:$embeddedDbTestVersion")
    testImplementation("io.zonky.test:embedded-postgres:2.0.7")

    // RestDocs → OpenAPI3 (테스트가 API 문서의 단일 출처)
    testImplementation("com.epages:restdocs-api-spec-mockmvc:0.19.2")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

ktlint {
    // Kotlin 2.1 호환 ktlint 엔진 (플러그인 기본값은 Kotlin 1.9 기반이라 충돌)
    version.set("1.5.0")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(files("src/main/kotlin"))
}

// detekt 1.23.7은 Kotlin 2.0.10로 컴파일됨 — detekt 분석 전용 클래스패스의
// Kotlin 의존성을 그 버전으로 고정해 프로젝트 Kotlin(2.1.0)과의 충돌을 막는다.
configurations.matching { it.name == "detekt" }.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.0.10")
        }
    }
}

// === RestDocs → OpenAPI3 (테스트가 문서의 단일 출처) ===
val snippetsDir = layout.buildDirectory.dir("generated-snippets")

tasks.withType<Test> {
    useJUnitPlatform()
    outputs.dir(snippetsDir)
    // 테스트는 임베디드 PG에 docs/sql DDL을 spring.sql.init로 적용한다(test 프로필). local은 OAuth 스텁/기본값 유지.
    systemProperty("spring.profiles.active", "local,test")
}

openapi3 {
    setServer(System.getenv("API_SERVER_URL") ?: "http://localhost:8080")
    title = "Flori Server API"
    description = "Spring REST Docs로 생성·검증된 Flori 백엔드 API 계약"
    version = "0.0.1"
    format = "json"
    outputFileNamePrefix = "open-api-3.0.1"
    outputDirectory = "src/main/resources/static/docs"
}

// openapi3 task는 플러그인이 평가 시점에 등록 → afterEvaluate에서 test 의존 연결
// (생성된 open-api-3.0.1.json은 src/main/resources/static/docs에 커밋되어 그대로 패키징됨)
afterEvaluate {
    // openapi3는 명시 실행(./gradlew openapi3)으로 스펙을 재생성·커밋한다.
    // bootJar에 걸지 않는 이유: 응답 예시값이 테스트 데이터라 비결정적 → 매 빌드 스펙 churn 방지.
    // (생성된 open-api-3.0.1.json은 static/docs에 커밋되어 jar에 그대로 패키징됨)
    tasks.named("openapi3") { dependsOn(tasks.test) }
}

// === JaCoCo 커버리지 (게이트 check 연결은 80% 달성 후 Task15에서) ===
jacoco { toolVersion = "0.8.12" }

val coverageExclusions =
    listOf(
        "**/FloriServerApplicationKt.*",
        "**/FloriServerApplication.*",
        "**/common/config/**",
        "**/dto/**",
    )

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } }),
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } }),
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

// 커버리지 게이트를 검증 파이프라인(check→build)에 연결 (현재 line 89%)
tasks.named("check") { dependsOn(tasks.jacocoTestCoverageVerification) }
