package kr.ai.flori.storage.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.storage.entity.UserStorage
import kr.ai.flori.storage.error.StorageErrorCode
import kr.ai.flori.storage.repository.UserStorageRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 사용량 뷰(점주 응답·내부 판정 공용). */
data class StorageUsage(
    val usedBytes: Long,
    val quotaBytes: Long,
    val percent: Int,
    val status: String,
)

/**
 * 갤러리 스토리지 쿼터 핵심. get-or-create(인당 1행) + 사용량 조회/검사/증감 + 관리자 증설/정합.
 * 사용량 증감은 [UserStorageRepository.addUsedBytes] 원자 UPDATE(경합 안전·0 클램프).
 */
@Service
class StorageQuotaService(
    private val repository: UserStorageRepository,
) {
    /**
     * 행 보장(없으면 기본 한도로 생성). 동시 생성 경합은 UNIQUE 위반을 잡아 재조회.
     * saveAndFlush로 INSERT를 try 블록 안에서 즉시 실행 → commit이 아닌 이 지점에서
     * [DataIntegrityViolationException]가 터져 catch가 동작한다.
     */
    @Transactional
    fun getOrCreate(userId: Long): UserStorage =
        repository.findByUserId(userId)
            ?: try {
                repository.saveAndFlush(UserStorage(userId = userId))
            } catch (e: DataIntegrityViolationException) {
                repository.findByUserId(userId) ?: throw e
            }

    @Transactional
    fun usage(userId: Long): StorageUsage {
        val s = getOrCreate(userId)
        val percent = if (s.quotaBytes <= 0) 0 else ((s.usedBytes * PERCENT_MULTIPLIER) / s.quotaBytes).toInt()
        val status =
            when {
                s.usedBytes >= s.quotaBytes -> STATUS_FULL
                percent >= WARN_PERCENT -> STATUS_WARN
                else -> STATUS_OK
            }
        return StorageUsage(s.usedBytes, s.quotaBytes, percent, status)
    }

    /** presign 발급 전 검사: 현재 사용량 + 추가 요청량이 한도를 넘으면 차단. */
    @Transactional(readOnly = true)
    fun requireWithinQuota(
        userId: Long,
        additionalBytes: Long,
    ) {
        val s = repository.findByUserId(userId) ?: return // 행 없음 = 사용량 0, 한도 내(추가량 ≤ 기본 한도면 통과)
        if (s.usedBytes + additionalBytes > s.quotaBytes) {
            throw AppException(StorageErrorCode.QUOTA_EXCEEDED)
        }
    }

    /** 카드 저장/삭제 시 사용량 원자 증감(감분은 음수). 행 보장 후 증감. */
    @Transactional
    fun addUsage(
        userId: Long,
        deltaBytes: Long,
    ) {
        if (deltaBytes == 0L) return
        getOrCreate(userId)
        repository.addUsedBytes(userId, deltaBytes)
    }

    /** 관리자 증설: 한도를 절대값으로 설정. managed 엔티티라 dirty-check로 flush됨. */
    @Transactional
    fun setQuota(
        userId: Long,
        quotaBytes: Long,
    ) {
        val s = getOrCreate(userId)
        s.quotaBytes = quotaBytes
    }

    /** 정합: 사용량을 DB 실측값으로 덮어쓴다. */
    @Transactional
    fun reconcile(
        userId: Long,
        actualBytes: Long,
    ) {
        getOrCreate(userId)
        repository.setUsedBytes(userId, actualBytes)
    }

    companion object {
        const val WARN_PERCENT = 90
        const val PERCENT_MULTIPLIER = 100
        const val STATUS_OK = "OK"
        const val STATUS_WARN = "WARN"
        const val STATUS_FULL = "FULL"
    }
}
