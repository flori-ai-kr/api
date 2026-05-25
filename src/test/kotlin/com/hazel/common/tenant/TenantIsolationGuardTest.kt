package com.hazel.common.tenant

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.support.Repositories
import java.lang.reflect.Method

/**
 * 멀티테넌시 격리 가드 (SPEC-016 / A1).
 *
 * hazel은 RLS가 없어 "애플리케이션이 유일한 방어선"이다(CLAUDE.md 보안 1순위).
 * 이 테스트는 모든 도메인 리포지토리가 "직접 선언한" 쿼리 메서드가 user_id로 테넌트
 * 격리되는지(메서드명에 UserId 포함 또는 @Query가 user_id 참조)를 리플렉션으로 전수 검증한다.
 * 격리되지 않은 메서드는 [intentionalGlobal] 화이트리스트(사유 명시)에만 허용된다.
 * 새 메서드가 user_id를 빠뜨리면 이 테스트가 실패해 데이터 유출 회귀를 막는다.
 *
 * cf. onetime/backend의 SecurityAnnotationTest(권한 어노테이션 누락 자동 검출)를
 *     hazel의 격리 모델에 맞게 이식.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class TenantIsolationGuardTest {
    @Autowired
    lateinit var applicationContext: ApplicationContext

    /**
     * user_id 격리 없이 호출돼도 안전한, 의도적으로 전역인 리포지토리 메서드.
     * 각 항목은 RF-001 audit에서 식별된 의도적 전역/자격증명 조회다.
     * 형식: "리포지토리Simple명#메서드명".
     */
    private val intentionalGlobal =
        setOf(
            // 인증: 사용자(테넌트 루트)·자격증명 조회 — 테넌트 데이터가 아님
            "UserRepository#findByEmail",
            "UserRepository#existsByEmail",
            "RefreshTokenRepository#findByTokenHash",
            // 스케줄러: 전체 테넌트 대상 시스템 작업(@Scheduled)
            "RecurringExpenseRepository#findByIsActiveTrue",
            "ReservationRepository#findDueReminders",
            "ReservationRepository#findByDateAndStatusNot",
            // 자식 엔티티: 이미 테넌트 검증된 부모(recurringId)로 접근
            "RecurringSkipRepository#existsByRecurringIdAndSkipDate",
            "RecurringSkipRepository#findByRecurringIdInAndSkipDate",
            // 인사이트 공유 콘텐츠: 트렌드/인스타는 전체 사용자 공유 데이터(엔티티에 user_id 없음) — 읽기/수집 전역(SPEC-011)
            "TrendArticleRepository#findByOrderByCollectedAtDescCreatedAtDesc",
            "TrendArticleRepository#findByCategoryOrderByCollectedAtDescCreatedAtDesc",
            "TrendArticleRepository#existsBySourceUrl",
            "TrendArticleRepository#countByCategory",
            "InstagramAccountRepository#findByActiveTrueOrderBySortOrderAscUsernameAsc",
            "InstagramAccountRepository#findAllByOrderBySortOrderAscUsernameAsc",
            "InstagramAccountRepository#findByUsername",
            "InstagramPostRepository#findFeed",
            "InstagramPostRepository#findFeedByAccount",
            "InstagramPostRepository#findWithAccountByIdIn",
            "InstagramPostRepository#existsByShortcode",
        )

    @Test
    fun `모든 리포지토리 선언 메서드는 user_id 격리되거나 의도적 전역으로 허용돼야 한다`() {
        val violations = mutableListOf<String>()
        var checked = 0

        for (repoInterface in hazelRepositoryInterfaces()) {
            for (method in repoInterface.declaredMethods) {
                checked++
                val key = "${repoInterface.simpleName}#${method.name}"
                if (isTenantScoped(method) || key in intentionalGlobal) continue
                violations.add(key)
            }
        }

        assertThat(checked).describedAs("스캔된 리포지토리 메서드가 0개 — 스캔 로직 점검 필요").isGreaterThan(0)
        assertThat(violations)
            .describedAs(
                "user_id 격리 누락 의심 메서드. 테넌트 격리(메서드명 UserId 또는 @Query user_id 참조)를 " +
                    "추가하거나, 정말 전역이면 intentionalGlobal 화이트리스트에 사유와 함께 등록하세요.",
            ).isEmpty()
    }

    @Test
    fun `화이트리스트는 모두 실재하고 실제로 비격리 메서드여야 한다`() {
        val interfaces = hazelRepositoryInterfaces()

        for (entry in intentionalGlobal) {
            val (ifaceName, methodName) = entry.split("#")
            val cls =
                interfaces.firstOrNull { it.simpleName == ifaceName }
            assertThat(cls).describedAs("화이트리스트 항목 $entry 의 리포지토리 인터페이스가 존재해야 함").isNotNull
            val methods = cls!!.declaredMethods.filter { it.name == methodName }
            assertThat(methods).describedAs("화이트리스트 항목 $entry 의 메서드가 실재해야 함").isNotEmpty
            assertThat(methods.none { isTenantScoped(it) })
                .describedAs("$entry 는 비격리 메서드여야 화이트리스트로서 의미가 있음(격리됐다면 화이트리스트에서 제거)")
                .isTrue()
        }
    }

    /** com.hazel 패키지의 Spring Data 리포지토리 인터페이스 목록. */
    private fun hazelRepositoryInterfaces(): List<Class<*>> {
        val repositories = Repositories(applicationContext)
        return repositories
            .mapNotNull { domainType ->
                repositories.getRepositoryInformationFor(domainType).orElse(null)?.repositoryInterface
            }.filter { it.name.startsWith("com.hazel") }
            .distinct()
    }

    /** 메서드명에 UserId가 있거나, @Query가 user_id/userId를 참조하면 테넌트 격리된 것으로 본다. */
    private fun isTenantScoped(method: Method): Boolean {
        if (method.name.lowercase().contains("userid")) return true
        val query = method.getAnnotation(Query::class.java) ?: return false
        val q = query.value.lowercase()
        return q.contains("user_id") || q.contains("userid")
    }
}
