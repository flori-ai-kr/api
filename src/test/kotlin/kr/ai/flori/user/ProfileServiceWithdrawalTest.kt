package kr.ai.flori.user

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.user.entity.User
import kr.ai.flori.user.entity.UserProfile
import kr.ai.flori.user.repository.UserProfileRepository
import kr.ai.flori.user.repository.UserRepository
import kr.ai.flori.user.service.ProfileService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

/**
 * 탈퇴(deleteAccount) 회귀 테스트.
 *
 * 탈퇴 후 원래 소셜 신원 (provider, providerId)이 해제되어 같은 소셜 계정으로
 * 깨끗하게 재가입(신규 신원)할 수 있어야 한다. 예전에는 provider_id를 스크럽하지 않아
 * (provider, provider_id) UNIQUE가 죽은 행에 묶여 재로그인이 영구 차단됐다.
 *
 * AuthFlowIntegrationTest와 동일하게 실제 PostgreSQL(Zonky) + @SpringBootTest로 검증한다.
 * (@Transactional을 붙이지 않아 save/deleteById가 실제로 반영된 상태를 조회로 확인한다.)
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class ProfileServiceWithdrawalTest {
    @Autowired
    lateinit var profileService: ProfileService

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var userProfileRepository: UserProfileRepository

    @Test
    fun `탈퇴 후 원래 소셜 신원이 해제되어 재가입이 가능하다`() {
        val providerId = "kakao-rejoin-test"
        val unique = UUID.randomUUID().toString().take(8)
        val user =
            userRepository.save(
                User(
                    email = "rejoin-$unique@flori.dev",
                    nickname = "재가입테스트-$unique",
                    provider = "KAKAO",
                    providerId = providerId,
                ),
            )
        val userId = user.id!!
        userProfileRepository.save(
            UserProfile(
                userId = userId,
                storeName = "재가입 플라워",
                phoneNumber = "01012345678",
                regionSido = "서울특별시",
            ),
        )

        // 탈퇴 전: 원래 신원으로 조회되어야 함
        assertThat(userRepository.findByProviderAndProviderId("KAKAO", providerId)).isNotNull

        profileService.deleteAccount(userId, reason = null, detail = null)

        // 원래 신원 해제 → 같은 소셜 계정 재가입 시 신규 신원으로 취급됨
        assertThat(userRepository.findByProviderAndProviderId("KAKAO", providerId)).isNull()

        // 계정 행은 남되 비활성화 + providerId가 withdrawn_ 더미로 스크럽됨
        val withdrawn = userRepository.findById(userId).orElseThrow()
        assertThat(withdrawn.isActive).isFalse()
        assertThat(withdrawn.providerId).startsWith("withdrawn_")

        // 프로필은 하드 삭제됨
        assertThat(userProfileRepository.findById(userId)).isEmpty
    }
}
