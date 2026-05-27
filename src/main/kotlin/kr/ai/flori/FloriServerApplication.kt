package kr.ai.flori

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.util.TimeZone

@SpringBootApplication
class FloriServerApplication

/**
 * 앱 JVM 기본 시간대를 UTC로 고정한다.
 *
 * 왜: `application.yml`의 `hibernate.jdbc.time_zone=UTC`는 JDBC 드라이버에 UTC Calendar를 넘긴다.
 * `time without time zone`(Kotlin `LocalTime`) 컬럼은 절대 시점이 아니므로, JVM 기본 시간대가
 * UTC가 아니면(예: 개발 IDE의 KST) 드라이버가 (JVM zone − jdbc.time_zone) 오프셋만큼 값을 이동시켜
 * 16:00 → 01:00(+9h)처럼 어긋난다. JVM을 UTC로 고정하면 오프셋 차가 0이 되어 `LocalTime`이
 * 시간대와 무관하게 정확히 왕복한다. (KST 개발·UTC 컨테이너 양쪽에서 동일하게 정확)
 *
 * `Instant`/timestamptz(`createdAt`/`updatedAt`/`reminderAt`)는 절대 시점이라 영향 없음.
 * 비즈니스 로직은 모두 명시적 KST(`ZoneId.of("Asia/Seoul")`)를 사용하므로 기본 시간대 변경의
 * 부작용이 없다(JVM 기본 시간대에 암묵 의존하는 `.now()` 호출 없음).
 *
 * `runApplication` 이전에 호출해야 한다(HikariCP/Hibernate/pgjdbc 초기화 전에 적용되어야 함).
 */
fun pinDefaultTimeZoneToUtc() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
}

fun main(args: Array<String>) {
    pinDefaultTimeZoneToUtc()
    runApplication<FloriServerApplication>(*args)
}
