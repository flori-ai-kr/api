package kr.ai.flori.verification.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.verification.dto.BusinessVerificationResponse
import kr.ai.flori.verification.dto.BusinessVerificationSubmitRequest
import kr.ai.flori.verification.error.VerificationErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest(properties = ["aws.cloudfront.domain=cdn.example.com"])
class BusinessVerificationServiceIntegrationTest {
    @Autowired private lateinit var service: BusinessVerificationService

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private fun newUserId(): Long {
        val token = TestAccounts.register(authService, tokenProvider)
        return tokenProvider.parse(token.accessToken)!!.userId
    }

    private fun licenseUrlFor(userId: Long) = "https://cdn.example.com/business-licenses/$userId/abc.jpg"

    @AfterEach
    fun tearDown() = TenantContext.clear()

    @Test
    fun `신청하면 PENDING 상태가 되고 상태조회로 확인된다`() {
        val userId = newUserId()
        TenantContext.set(userId)

        service.submit(
            BusinessVerificationSubmitRequest(
                businessNumber = "1234567890",
                businessName = "플로리 꽃집",
                representativeName = "홍길동",
                businessLicenseUrl = licenseUrlFor(userId),
            ),
        )

        assertThat(service.getMyStatus().status).isEqualTo("PENDING")
    }

    @Test
    fun `이력이 없으면 NONE을 반환한다`() {
        val userId = newUserId()
        TenantContext.set(userId)

        assertThat(service.getMyStatus().status).isEqualTo(BusinessVerificationResponse.STATUS_NONE)
    }

    @Test
    fun `PENDING이 있는데 또 신청하면 ALREADY_REQUESTED 409`() {
        val userId = newUserId()
        TenantContext.set(userId)
        val req = BusinessVerificationSubmitRequest("1234567890", "플로리 꽃집", "홍길동", licenseUrlFor(userId))
        service.submit(req)

        val ex = assertThrows<AppException> { service.submit(req) }
        assertThat(ex.errorCode).isEqualTo(VerificationErrorCode.ALREADY_REQUESTED)
    }

    @Test
    fun `남의 prefix 등록증 URL은 LICENSE_NOT_OWNED`() {
        val userId = newUserId()
        TenantContext.set(userId)

        val ex =
            assertThrows<AppException> {
                service.submit(
                    BusinessVerificationSubmitRequest(
                        "1234567890",
                        "플로리 꽃집",
                        "홍길동",
                        "https://cdn.example.com/business-licenses/99999/abc.jpg",
                    ),
                )
            }
        assertThat(ex.errorCode).isEqualTo(VerificationErrorCode.LICENSE_NOT_OWNED)
    }

    @Test
    fun `외부 호스트 등록증 URL은 본인 prefix여도 LICENSE_NOT_OWNED`() {
        val userId = newUserId()
        TenantContext.set(userId)

        val ex =
            assertThrows<AppException> {
                service.submit(
                    BusinessVerificationSubmitRequest(
                        "1234567890",
                        "플로리 꽃집",
                        "홍길동",
                        // prefix는 본인 것이지만 호스트가 우리 CDN(cdn.example.com)이 아님
                        "https://attacker.com/business-licenses/$userId/abc.jpg",
                    ),
                )
            }
        assertThat(ex.errorCode).isEqualTo(VerificationErrorCode.LICENSE_NOT_OWNED)
    }

    @Test
    fun `isVerified는 APPROVED 행이 없으면 false`() {
        val userId = newUserId()
        TenantContext.set(userId)
        assertThat(service.isVerified(userId)).isFalse()
    }
}
