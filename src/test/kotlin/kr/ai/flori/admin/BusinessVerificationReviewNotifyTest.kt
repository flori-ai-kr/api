package kr.ai.flori.admin

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.notification.solapi.SolapiNotifier
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import kr.ai.flori.verification.entity.BusinessVerification
import kr.ai.flori.verification.repository.BusinessVerificationRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class BusinessVerificationReviewNotifyTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var verificationRepository: BusinessVerificationRepository

    @MockitoBean private lateinit var solapiNotifier: SolapiNotifier

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T

    private fun captureStr(captor: ArgumentCaptor<String>): String {
        captor.capture()
        return uninitialized()
    }

    private fun adminToken(): String {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val user = userRepository.findById(tokenProvider.parse(tokens.accessToken)!!.userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)
        return tokens.accessToken
    }

    private fun pendingFor(): Long {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val uid = tokenProvider.parse(tokens.accessToken)!!.userId
        return verificationRepository
            .save(
                BusinessVerification(
                    userId = uid,
                    businessNumber = "1234567890",
                    businessName = "플로리",
                    representativeName = "홍길동",
                    businessLicenseUrl = "https://cdn.example.com/business-licenses/$uid/a.jpg",
                ),
            ).id!!
    }

    @Test
    fun `거절하면 사유 포함 거절 알림톡이 발송된다`() {
        val token = adminToken()
        val id = pendingFor()

        mockMvc
            .post("/admin/verifications/$id/reject") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                content = """{"reason":"등록증 사진이 흐립니다"}"""
            }.andExpect { status { isOk() } }

        val reasonCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(solapiNotifier).sendBusinessRejected(anyLong(), anyString(), anyString(), captureStr(reasonCaptor))
        assertThat(reasonCaptor.value).isEqualTo("등록증 사진이 흐립니다")
        Mockito.verify(solapiNotifier, Mockito.never()).sendBusinessApproved(anyLong(), anyString(), anyString())
    }

    @Test
    fun `승인하면 승인 알림톡만 발송되고 거절 알림톡은 없다`() {
        val token = adminToken()
        val id = pendingFor()

        mockMvc
            .post("/admin/verifications/$id/approve") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }

        Mockito.verify(solapiNotifier).sendBusinessApproved(anyLong(), anyString(), anyString())
        Mockito
            .verify(solapiNotifier, Mockito.never())
            .sendBusinessRejected(anyLong(), anyString(), anyString(), anyString())
    }
}
