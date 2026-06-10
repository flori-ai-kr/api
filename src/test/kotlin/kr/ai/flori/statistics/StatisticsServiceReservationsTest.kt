package kr.ai.flori.statistics

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.pinDefaultTimeZoneToUtc
import kr.ai.flori.reservations.dto.ReservationCreateRequest
import kr.ai.flori.reservations.service.ReservationService
import kr.ai.flori.statistics.service.StatisticsService
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.time.LocalTime
import java.util.TimeZone
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class StatisticsServiceReservationsTest {
    @Autowired
    lateinit var statisticsService: StatisticsService

    @Autowired
    lateinit var reservationService: ReservationService

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    companion object {
        private lateinit var originalZone: TimeZone

        /**
         * 프로덕션 JVM 시간대 패리티: main()과 동일하게 JVM 기본 시간대를 UTC로 고정한다.
         * 클래스 로드 시점(Spring 컨텍스트/HikariCP/pgjdbc 초기화 이전)에 적용되어야 하므로
         * @BeforeAll이 아닌 companion init 블록에서 고정한다 — @SpringBootTest는 컨텍스트가
         * @BeforeAll보다 먼저 부팅될 수 있다. 이로써 LocalTime(15,30) 삽입이 DB에 15:30으로
         * 그대로 들어가고(환산 없음), "time" 직접 버킷팅이 "15-17"을 산출한다.
         */
        init {
            originalZone = TimeZone.getDefault()
            pinDefaultTimeZoneToUtc()
        }

        @JvmStatic
        @BeforeAll
        fun pinZone() {
            // 안전망: 다른 테스트가 기본 시간대를 바꿔놨더라도 본 클래스 실행 동안 UTC 보장.
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
        val email = "stats-resv-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun reservation(
        date: LocalDate,
        time: LocalTime?,
    ) = reservationService.create(
        ReservationCreateRequest(
            date = date,
            time = time,
            customerName = "홍길동",
            title = "픽업 꽃다발",
        ),
    )

    @Test
    fun `예약 통계는 요일·시간대 분포·히트맵·KPI를 산출한다`() {
        newTenant()
        // 2026-06-06 토요일, 2026-06-08 월요일
        reservation(LocalDate.of(2026, 6, 6), LocalTime.of(15, 30))
        reservation(LocalDate.of(2026, 6, 6), LocalTime.of(16, 0))
        reservation(LocalDate.of(2026, 6, 8), LocalTime.of(10, 0))

        val result = statisticsService.reservationStatistics(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

        assertThat(result.kpi.total).isEqualTo(3)
        assertThat(result.kpi.busiestDow).isEqualTo(6) // 토요일
        assertThat(result.kpi.busiestDowPct).isEqualTo(67) // 2/3 반올림
        assertThat(result.kpi.peakHourBucket).isEqualTo("15-17")
        assertThat(result.kpi.peakHourPct).isEqualTo(67)

        assertThat(result.dowDistribution).anySatisfy {
            assertThat(it.dow).isEqualTo(6)
            assertThat(it.count).isEqualTo(2)
        }
        assertThat(result.dowDistribution).anySatisfy {
            assertThat(it.dow).isEqualTo(1)
            assertThat(it.count).isEqualTo(1)
        }

        assertThat(result.hourDistribution).anySatisfy {
            assertThat(it.hourBucket).isEqualTo("15-17")
            assertThat(it.count).isEqualTo(2)
        }
        assertThat(result.hourDistribution).anySatisfy {
            assertThat(it.hourBucket).isEqualTo("09-11")
            assertThat(it.count).isEqualTo(1)
        }

        assertThat(result.heatmap).anySatisfy {
            assertThat(it.dow).isEqualTo(6)
            assertThat(it.hourBucket).isEqualTo("15-17")
            assertThat(it.count).isEqualTo(2)
        }
    }

    @Test
    fun `시간 미지정 예약은 총건수에 포함되나 시간대·히트맵 집계에서 제외된다`() {
        newTenant()
        reservation(LocalDate.of(2026, 6, 6), LocalTime.of(15, 30))
        reservation(LocalDate.of(2026, 6, 8), null)

        val result = statisticsService.reservationStatistics(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

        assertThat(result.kpi.total).isEqualTo(2)
        // 시간대 집계는 NULL time 제외 → 15-17 1건만
        assertThat(result.hourDistribution.sumOf { it.count }).isEqualTo(1)
        assertThat(result.heatmap.sumOf { it.count }).isEqualTo(1)
        // dow 분포는 NULL time 포함 → 토요일·월요일 각 1건
        assertThat(result.dowDistribution.sumOf { it.count }).isEqualTo(2)
    }

    @Test
    fun `다른 테넌트의 예약은 집계에 포함되지 않는다`() {
        newTenant()
        reservation(LocalDate.of(2026, 6, 6), LocalTime.of(15, 30))

        newTenant() // 새 사용자(데이터 없음)
        val result = statisticsService.reservationStatistics(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

        assertThat(result.kpi.total).isZero()
        assertThat(result.kpi.busiestDow).isEqualTo(-1)
        assertThat(result.kpi.peakHourBucket).isEmpty()
        assertThat(result.timeseries).isEmpty()
        assertThat(result.heatmap).isEmpty()
        assertThat(result.dowDistribution).isEmpty()
        assertThat(result.hourDistribution).isEmpty()
    }

    @Test
    fun `from이 to보다 뒤면 검증 에러(400)를 던진다`() {
        newTenant()
        assertThatThrownBy {
            statisticsService.reservationStatistics(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 1))
        }.isInstanceOf(AppException::class.java)
            .extracting { (it as AppException).errorCode }
            .isEqualTo(CommonErrorCode.VALIDATION)
    }
}
