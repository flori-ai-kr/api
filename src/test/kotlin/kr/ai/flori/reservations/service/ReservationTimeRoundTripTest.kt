package kr.ai.flori.reservations.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import jakarta.persistence.EntityManager
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.pinDefaultTimeZoneToUtc
import kr.ai.flori.reservations.dto.ReservationCreateRequest
import kr.ai.flori.reservations.dto.ReservationUpdateRequest
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID
import javax.sql.DataSource

/**
 * `time without time zone`(Kotlin `LocalTime`) 컬럼의 시간대 왕복 회귀 테스트.
 *
 * 버그: `hibernate.jdbc.time_zone=UTC`는 JDBC 레이어를 `getTime(cal)`(UTC Calendar) 경로로 유도한다.
 * JVM 기본 시간대가 KST(개발 IDE)면 이 경로가 (KST − UTC)=+9h 만큼 값을 이동시켜, DB에 올바르게
 * 저장된 16:00을 읽을 때 01:00으로 어긋난다(raw-JDBC로 실측 확인). 이것이 사용자가 본 +9h 증상이다.
 *
 * 수정: `pinDefaultTimeZoneToUtc()`로 JVM 기본 시간대를 UTC로 고정하면 오프셋 차가 0이 되어 Calendar
 * 경로조차 정확히 읽는다. 이 테스트는 의도적으로 JVM을 KST로 두고(버그 조건) 수정 메커니즘을 적용한 뒤,
 * DB에 올바른 16:00이 있는 상태에서 `getTime(UTC Calendar)` 읽기가 16:00을 그대로 돌려주는지 검증한다.
 * 수정이 없으면(JVM이 KST로 남으면) 해당 단언은 01:00을 보고 실패한다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class ReservationTimeRoundTripTest {
    @Autowired
    lateinit var service: ReservationService

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var em: EntityManager

    @Autowired
    lateinit var dataSource: DataSource

    companion object {
        private lateinit var originalZone: TimeZone

        @JvmStatic
        @BeforeAll
        fun pinZone() {
            // 1) 버그 조건 재현: JVM 기본 시간대를 KST로 강제(개발 IDE 환경).
            originalZone = TimeZone.getDefault()
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
            // 2) 프로덕션 수정 적용: main()이 호출하는 동일한 메커니즘.
            pinDefaultTimeZoneToUtc()
        }

        @JvmStatic
        @AfterAll
        fun restoreZone() {
            TimeZone.setDefault(originalZone)
        }
    }

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long {
        val email = "resv-tz-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    /**
     * 사용자가 본 +9h 증상의 결정적 재현·회귀 단언.
     *
     * DB에는 올바른 16:00이 이미 들어 있다(psql 검증과 동일하게 SQL 리터럴로 직접 삽입 → 쓰기 경로의
     * 어떤 이동도 개입하지 않음). 이를 `getTime(UTC Calendar)`(= jdbc.time_zone=UTC가 유도하는 읽기 경로)로
     * 읽는다. 수정(JVM=UTC 고정)이 적용되면 16:00을 그대로 돌려주고, 수정이 없으면(JVM=KST) 01:00이 되어 실패한다.
     */
    @Test
    fun `DB의 올바른 16시 time 값을 UTC Calendar로 읽어도 16시여야 한다`() {
        val utcCal = { Calendar.getInstance(TimeZone.getTimeZone("UTC")) }
        dataSource.connection.use { c ->
            c.createStatement().use { st ->
                st.execute("DROP TABLE IF EXISTS tz_roundtrip_probe")
                st.execute("CREATE TABLE tz_roundtrip_probe (t time)")
                // DB에는 올바른 16:00이 이미 저장돼 있다(쓰기 측 이동 없음).
                st.execute("INSERT INTO tz_roundtrip_probe (t) VALUES (TIME '16:00:00')")
            }
            c.prepareStatement("SELECT t FROM tz_roundtrip_probe").use { ps ->
                ps.executeQuery().use { rs ->
                    assertThat(rs.next()).isTrue()
                    val readBack = rs.getTime(1, utcCal()).toLocalTime()
                    assertThat(readBack).isEqualTo(LocalTime.of(16, 0))
                }
            }
        }
    }

    @Test
    @Transactional
    fun `LocalTime 16시는 JPA DB 왕복 후에도 16시로 유지된다`() {
        newTenant()
        val created =
            service.create(
                ReservationCreateRequest(
                    date = LocalDate.of(2026, 5, 27),
                    time = LocalTime.of(16, 0),
                    customerName = "홍길동",
                    title = "픽업",
                ),
            )

        // 1차 영속성 컨텍스트 캐시가 아니라 실제 DB에서 다시 읽도록 flush + clear.
        em.flush()
        em.clear()

        val reloaded = service.get(created.id)
        assertThat(reloaded.time).isEqualTo(LocalTime.of(16, 0))
    }

    @Test
    @Transactional
    fun `Instant(timestamptz)는 시간대 고정 후에도 정확히 왕복한다`() {
        newTenant()
        // KST 09:00 = UTC 00:00 인 절대 시점.
        val reminder = OffsetDateTime.of(2026, 5, 27, 9, 0, 0, 0, ZoneOffset.ofHours(9)).toInstant()
        val created =
            service.create(
                ReservationCreateRequest(
                    date = LocalDate.of(2026, 5, 27),
                    time = LocalTime.of(16, 0),
                    customerName = "김영희",
                    title = "픽업",
                ),
            )
        service.update(created.id, ReservationUpdateRequest(reminderAt = reminder))

        em.flush()
        em.clear()

        val reloaded = service.get(created.id)
        assertThat(reloaded.reminderAt).isEqualTo(reminder)
        // createdAt(BaseEntity, timestamptz)도 손상 없이 존재.
        assertThat(reloaded.createdAt).isNotNull()
        assertThat(reloaded.createdAt).isBeforeOrEqualTo(Instant.now())
    }
}
