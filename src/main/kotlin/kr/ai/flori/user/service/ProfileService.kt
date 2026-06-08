package kr.ai.flori.user.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.storage.S3PresignService
import kr.ai.flori.user.dto.FullProfileResponse
import kr.ai.flori.user.dto.ProfileUploadTargetResponse
import kr.ai.flori.user.repository.UserProfileRepository
import kr.ai.flori.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ProfileService(
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
    private val s3PresignService: S3PresignService,
) {
    fun getFullProfile(userId: Long): FullProfileResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { AppException(CommonErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다") }
        val profile =
            userProfileRepository
                .findById(userId)
                .orElseThrow { AppException(CommonErrorCode.NOT_FOUND, "프로필을 찾을 수 없습니다") }

        return FullProfileResponse(
            id = userId,
            name = profile.storeName,
            nickname = user.nickname,
            email = user.email,
            profileImageUrl = profile.profileImageUrl,
            regionSido = profile.regionSido,
            regionSigungu = profile.regionSigungu,
            ownerAgeRange = profile.ownerAgeRange,
            interests = profile.interests.toList(),
            specialties = profile.specialties.toList(),
        )
    }

    fun createUploadTarget(
        userId: Long,
        contentType: String,
    ): ProfileUploadTargetResponse {
        if (!contentType.startsWith("image/")) {
            throw AppException(CommonErrorCode.VALIDATION, "이미지 타입만 업로드할 수 있습니다")
        }
        val ext = CONTENT_TYPE_EXT[contentType] ?: "jpg"
        val key = "profiles/$userId/${UUID.randomUUID()}.$ext"
        val presigned = s3PresignService.presignUpload(key, contentType)
        return ProfileUploadTargetResponse(
            uploadUrl = presigned.uploadUrl,
            publicUrl = presigned.fileUrl,
        )
    }

    @Transactional
    fun completeTour(userId: Long) {
        val profile =
            userProfileRepository
                .findById(userId)
                .orElseThrow { AppException(CommonErrorCode.NOT_FOUND, "프로필을 찾을 수 없습니다") }
        profile.tourCompleted = true
        userProfileRepository.save(profile)
    }

    @Transactional
    @Suppress("UnusedParameter")
    fun deleteAccount(
        userId: Long,
        reason: String?,
        detail: String?,
    ) {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { AppException(CommonErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다") }

        // soft delete — 계정 비활성화
        user.isActive = false
        // 탈퇴 시 고유 제약(email/nickname/소셜 신원) 해제: 임의 값으로 덮어쓰기.
        // provider_id까지 스크럽해야 같은 소셜 계정으로 깨끗하게 재가입(신규 신원)할 수 있다
        // (안 풀면 (provider, provider_id) UNIQUE가 죽은 행에 묶여 영구 차단됨).
        val suffix = UUID.randomUUID().toString().take(SUFFIX_LENGTH)
        user.email = "withdrawn_${userId}_$suffix@deleted"
        user.nickname = "탈퇴회원_${userId}_$suffix"
        user.providerId = "withdrawn_${userId}_$suffix"
        userRepository.save(user)

        // 프로필 삭제
        userProfileRepository.deleteById(userId)
    }

    private companion object {
        const val SUFFIX_LENGTH = 8
        val CONTENT_TYPE_EXT =
            mapOf(
                "image/jpeg" to "jpg",
                "image/png" to "png",
                "image/webp" to "webp",
                "image/heic" to "heic",
            )
    }
}
