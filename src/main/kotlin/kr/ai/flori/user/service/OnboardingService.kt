package kr.ai.flori.user.service

import kr.ai.flori.auth.error.AuthErrorCode
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.user.dto.OnboardingRequest
import kr.ai.flori.user.dto.ProfileResponse
import kr.ai.flori.user.dto.UserResponse
import kr.ai.flori.user.entity.UserProfile
import kr.ai.flori.user.repository.UserProfileRepository
import kr.ai.flori.user.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 온보딩 서비스: 가게 프로필 upsert + 닉네임(users.nickname) 편집.
 *
 * 멀티테넌시: 항상 호출부가 넘긴 currentUserId만 키로 사용한다. 요청 본문의 user_id는 받지 않는다
 * (UserProfile PK = user_id이므로 본질적으로 테넌트 격리). 같은 사용자가 두 번 제출하면 멱등 갱신.
 * 닉네임도 항상 currentUserId 행에만 적용되며, 전역 유일성은 본인 제외 검사로 강제한다.
 */
@Service
class OnboardingService(
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
) {
    /** 가게 프로필을 insert/update하고(닉네임이 오면 함께 변경) 갱신된 사용자 응답을 반환한다. */
    @Transactional
    fun submit(
        userId: Long,
        request: OnboardingRequest,
    ): UserResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { AppException(CommonErrorCode.UNAUTHORIZED) }

        // 이메일 변경 요청이 있을 때만 중복 검사 후 적용.
        val newEmail = request.email?.takeIf { it.isNotBlank() }
        if (newEmail != null && newEmail != user.email) {
            if (userRepository.existsByEmail(newEmail)) {
                throw AppException(AuthErrorCode.DUPLICATE_EMAIL, "이미 사용 중인 이메일입니다")
            }
            user.email = newEmail
        }

        // 닉네임 변경 요청이 있을 때만 유일성 검사 후 적용. 본인 기존 닉네임 유지는 오탐하지 않는다.
        val newNickname = request.nickname?.takeIf { it.isNotBlank() }
        if (newNickname != null && newNickname != user.nickname) {
            if (userRepository.existsByNicknameAndIdNot(newNickname, userId)) {
                throw AppException(AuthErrorCode.DUPLICATE_NICKNAME, DUP_NICKNAME)
            }
            user.nickname = newNickname
            try {
                // 동시성 경쟁(uq_users_nickname)에 대비해 즉시 flush, 충돌 시 멱등하게 닉네임 중복으로 변환.
                userRepository.saveAndFlush(user)
            } catch (_: DataIntegrityViolationException) {
                throw AppException(AuthErrorCode.DUPLICATE_NICKNAME, DUP_NICKNAME)
            }
        }

        val profile =
            userProfileRepository
                .findById(userId)
                .orElseGet {
                    UserProfile(
                        userId = userId,
                        storeName = request.name,
                        regionSido = request.regionSido,
                    )
                }
        profile.storeName = request.name
        profile.regionSido = request.regionSido
        profile.regionSigungu = request.regionSigungu
        profile.ownerAgeRange = request.ownerAgeRange
        profile.interests = request.interests?.toTypedArray() ?: emptyArray()
        profile.specialties = request.specialties?.toTypedArray() ?: emptyArray()
        if (request.profileImageUrl != null) {
            profile.profileImageUrl = request.profileImageUrl
        }
        val saved = userProfileRepository.save(profile)

        return UserResponse(
            id = userId,
            email = user.email,
            nickname = user.nickname,
            profile = saved.toResponse(),
        )
    }

    private companion object {
        const val DUP_NICKNAME = "이미 사용 중인 닉네임입니다"
    }
}

/** UserProfile → ProfileResponse 매핑. /me·온보딩 응답이 공유한다. */
fun UserProfile.toResponse(): ProfileResponse =
    ProfileResponse(
        storeName = storeName,
        regionSido = regionSido,
        regionSigungu = regionSigungu,
        ownerAgeRange = ownerAgeRange,
        interests = interests.toList(),
        specialties = specialties.toList(),
        profileImageUrl = profileImageUrl,
    )
