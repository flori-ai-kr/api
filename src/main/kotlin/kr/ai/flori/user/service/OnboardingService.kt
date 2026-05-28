package kr.ai.flori.user.service

import kr.ai.flori.auth.dto.UserResponse
import kr.ai.flori.auth.repository.UserRepository
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import kr.ai.flori.user.dto.OnboardingRequest
import kr.ai.flori.user.dto.ProfileResponse
import kr.ai.flori.user.entity.UserProfile
import kr.ai.flori.user.repository.UserProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 온보딩 서비스: 가게 프로필 upsert.
 *
 * 멀티테넌시: 항상 호출부가 넘긴 currentUserId만 키로 사용한다. 요청 본문의 user_id는 받지 않는다
 * (UserProfile PK = user_id이므로 본질적으로 테넌트 격리). 같은 사용자가 두 번 제출하면 멱등 갱신.
 */
@Service
class OnboardingService(
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
) {
    /** 가게 프로필을 insert/update한 뒤 갱신된 사용자 응답을 반환한다. */
    @Transactional
    fun submit(
        userId: Long,
        request: OnboardingRequest,
    ): UserResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { AppException(ErrorCode.UNAUTHORIZED) }

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
        val saved = userProfileRepository.save(profile)

        return UserResponse(
            id = userId,
            email = user.email,
            name = user.name,
            profile = saved.toResponse(),
        )
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
    )
