package kr.ai.flori.common.tenant

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
 * flori은 RLS가 없어 "애플리케이션이 유일한 방어선"이다(CLAUDE.md 보안 1순위).
 * 이 테스트는 모든 도메인 리포지토리가 "직접 선언한" 쿼리 메서드가 user_id로 테넌트
 * 격리되는지(메서드명에 UserId 포함 또는 @Query가 user_id 참조)를 리플렉션으로 전수 검증한다.
 * 격리되지 않은 메서드는 [intentionalGlobal] 화이트리스트(사유 명시)에만 허용된다.
 * 새 메서드가 user_id를 빠뜨리면 이 테스트가 실패해 데이터 유출 회귀를 막는다.
 *
 * cf. onetime/backend의 SecurityAnnotationTest(권한 어노테이션 누락 자동 검출)를
 *     flori의 격리 모델에 맞게 이식.
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
            // 인증: 닉네임(users.nickname) 전역 유일성 검사 — 전역 unique 제약(uq_users_nickname)이므로 테넌트 격리 대상 아님
            "UserRepository#existsByNickname",
            "UserRepository#existsByNicknameAndIdNot",
            // 인증: 소셜 신원(provider+providerId) 조회 — 자격증명 조회, 테넌트 데이터 아님 (SPEC-RN-015)
            "UserRepository#findByProviderAndProviderId",
            "RefreshTokenRepository#findByTokenHash",
            // 스케줄러: 전체 테넌트 대상 시스템 작업(@Scheduled)
            "RecurringExpenseRepository#findActiveDueCandidates",
            "ReservationRepository#findDueReminders",
            "ReservationRepository#findByDateAndStatusNot",
            // 자식 엔티티: 이미 테넌트 검증된 부모(recurringId)로 접근
            "RecurringSkipRepository#existsByRecurringIdAndSkipDate",
            "RecurringSkipRepository#findByRecurringIdInAndSkipDate",
            // 커뮤니티: 단일 커뮤니티(테넌트 간 공유) — 엔티티에 user_id 격리가 없고,
            // 비밀글/소유권/마스킹은 서비스가 뷰어(JWT)+author_user_id로 계산한다(설계상 전역 읽기/쓰기).
            "CommunityPostRepository#findByIdAndDeletedAtIsNull",
            "CommunityPostRepository#findByIdAndDeletedAtIsNullAndHiddenAtIsNull",
            "CommunityPostRepository#findFeed",
            "CommunityPostRepository#adjustLikeCount",
            "CommunityPostRepository#adjustCommentCount",
            "CommunityCommentRepository#findByIdAndDeletedAtIsNull",
            "CommunityCommentRepository#findByPostIdAndHiddenAtIsNullOrderByCreatedAtAsc",
            // 댓글 깊이 검증용 조상 체인 깊이(재귀 CTE) — 단일 커뮤니티(전역), 깊이만 계산하고 데이터 노출 없음
            "CommunityCommentRepository#ancestorDepth",
            // 커뮤니티 모더레이션(신고/차단): 단일 커뮤니티(전역) — 동일 대상 신고 집계/큐 조회.
            // 권한은 @RequiresBusinessVerified(신고) / @RequiresAdmin(처리·차단)로 보호된다.
            // (existsBy...ReporterUserId / findByUserIdAndLiftedAtIsNull 은 user_id 격리 메서드라 화이트리스트 불필요)
            "CommunityReportRepository#search",
            "CommunityReportRepository#countByTargetTypeAndTargetIdAndStatus",
            "CommunityBanRepository#findActive",
            // 운영 콘솔(admin): 사업자 인증 심사 — 의도적 cross-tenant 조회.
            // @RequiresAdmin 인터셉터(User.isAdmin 재검증)로만 보호되며 일반 점주는 접근 불가.
            "BusinessVerificationRepository#findByStatusOrderByCreatedAtDesc",
            // 운영 콘솔(admin): 감사로그·발송이력·브로드캐스트·문의 전역 조회 — @RequiresAdmin 으로만 보호.
            "NotificationSendLogRepository#search",
            "BroadcastRepository#search",
            "SupportInquiryRepository#search",
            // 백그라운드 작업(cron) 실행 로그: 시스템 전역 운영 로그(user 데이터 아님) — @RequiresAdmin 으로만 조회.
            "JobRunLogRepository#search",
            // 공지 배너: 단일 전역 테이블(테넌트 격리 없음) — 활성 공지는 모든 점주에게 동일 노출.
            "AnnouncementRepository#findByIdAndDeletedAtIsNull",
            "AnnouncementRepository#findActive",
            "AnnouncementRepository#findAllForAdmin",
            // AI 프롬프트 레지스트리(SPEC-AI-008): user 데이터가 아니라 운영 자산(전역 단일 테이블).
            // 접근은 콘솔(@RequiresAdmin)과 게이트웨이 내부 active 로드로만 제한된다.
            "AiPromptRepository#findFirstByChannelAndIsActiveTrueAndDeletedAtIsNull",
            "AiPromptRepository#findByChannelAndDeletedAtIsNullOrderByIsActiveDescCreatedAtDesc",
            "AiPromptRepository#findByIdAndDeletedAtIsNull",
            "AiPromptRepository#findByChannelAndVersionAndDeletedAtIsNull",
            // 대기자 명단(공개 모집): 인증/테넌시 없는 단일 전역 테이블 — email 중복 검사는 전역 unique 제약 대응
            "WaitlistRegistrationRepository#existsByEmail",
            // 유저 인터뷰 모집(공개): 인증/테넌시 없는 단일 전역 테이블 — phone 중복 검사는 전역 unique 제약 대응
            "InterviewRequestRepository#existsByPhone",
            // 인사이트(정보 피드): 공판장/지원사업은 테넌트 간 공유 읽기 테이블(설계상 전역).
            // user_id 격리가 없고 누구나 같은 큐레이션을 본다. 개인 데이터는 insight_scraps(전부 ...UserId 격리)뿐.
            "SupportProgramRepository#findFeed",
            "SupportProgramRepository#findByIdIn",
            // 빌링 — 구독 스케줄러(@Scheduled): 전체 테넌트 대상 시스템 작업.
            // 결제일 도래 배치 / D-3 사전알림 배치는 cross-tenant 시스템 작업이므로 의도적 전역.
            "SubscriptionRepository#findByStatusInAndNextBillingAtLessThanEqual",
            // 빌링 — 어드민 구독 집계/목록(@RequiresAdmin): cross-tenant 운영 콘솔 조회.
            "SubscriptionRepository#countByStatus",
            "SubscriptionRepository#findByStatusOrderByCreatedAtDesc",
            "SubscriptionRepository#findAllByOrderByCreatedAtDesc",
            // 빌링 — 결제 이력: subscriptionId는 이미 테넌트 검증된 부모로 접근.
            // orderId는 토스 페이먼츠 웹훅 수신 시 콜백 매칭 — 전역 유일 식별자로 서비스가 소유권 검증 후 처리.
            "PaymentHistoryRepository#existsBySubscriptionIdAndBillingCycleAndStatus",
            "PaymentHistoryRepository#countBySubscriptionIdAndBillingCycle",
            "PaymentHistoryRepository#findTop10BySubscriptionIdOrderByCreatedAtDesc",
            "PaymentHistoryRepository#findByOrderId",
            "PaymentHistoryRepository#findByTossPaymentKey", // 웹훅 콜백 매칭(전역 paymentKey)
            // 빌링 — 쿠폰: 코드는 전역 유일(운영자가 발행) — 유저가 입력한 코드로 조회 후 서비스가 사용 권한 검증.
            "CouponRepository#findByCode",
            "CouponRepository#existsByCode",
            // 빌링 — 쿠폰 사용 이력: couponId 기준 어드민 조회 — @RequiresAdmin으로 보호된 운영자 조회.
            "CouponRedemptionRepository#findByCouponIdOrderByCreatedAtDesc",
            // 빌링 — 구독 신원 원장: identity_hash 조회 — 탈퇴 유저 포함 전체 원장 대상 어뷰징 방어 조회.
            // user_id가 없는 설계(탈퇴 후도 유지 목적) — 서비스가 hash로만 접근.
            "SubscriptionEligibilityRepository#findByIdentityHash",
            // 스토리지 증설 요청 운영 콘솔 조회: cross-tenant — @RequiresAdmin 하위에서만 사용.
            "StorageIncreaseRequestRepository#search",
        )

    @Test
    fun `모든 리포지토리 선언 메서드는 user_id 격리되거나 의도적 전역으로 허용돼야 한다`() {
        val violations = mutableListOf<String>()
        var checked = 0

        for (repoInterface in floriRepositoryInterfaces()) {
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
        val interfaces = floriRepositoryInterfaces()

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

    /** kr.ai.flori 패키지의 Spring Data 리포지토리 인터페이스 목록. */
    private fun floriRepositoryInterfaces(): List<Class<*>> {
        val repositories = Repositories(applicationContext)
        return repositories
            .mapNotNull { domainType ->
                repositories.getRepositoryInformationFor(domainType).orElse(null)?.repositoryInterface
            }.filter { it.name.startsWith("kr.ai.flori") }
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
