package kr.ai.flori.storage.job

import kr.ai.flori.common.job.JobNames
import kr.ai.flori.common.job.JobOutcome
import kr.ai.flori.common.job.JobRunRecorder
import kr.ai.flori.photos.repository.PhotoCardRepository
import kr.ai.flori.storage.repository.UserStorageRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 갤러리 스토리지 사용량 정합. DB의 PhotoFile.size 합(SSOT)으로 user_storage.used_bytes를 보정한다.
 * 증감 누락·구 데이터(size=0)·경합 드리프트를 주기적으로 true-up. 매일 새벽 KST.
 */
@Component
class StorageReconcileJob(
    private val photoCardRepository: PhotoCardRepository,
    private val userStorageRepository: UserStorageRepository,
    private val jobRunRecorder: JobRunRecorder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${flori.storage-reconcile.cron:0 50 4 * * *}", zone = "Asia/Seoul")
    fun scheduledReconcile() = jobRunRecorder.record(JobNames.STORAGE_RECONCILE) { runReconcile() }

    @Transactional
    fun runReconcile(): JobOutcome {
        val sums =
            photoCardRepository
                .sumPhotoBytesByUser()
                .associate { (it[0] as Number).toLong() to (it[1] as Number).toLong() }
        var updated = 0
        // user_storage 가 있는 모든 유저를 실측값(없으면 0)으로 보정
        userStorageRepository.findAll().forEach { row ->
            val actual = sums[row.userId] ?: 0L
            if (row.usedBytes != actual) {
                userStorageRepository.setUsedBytes(row.userId, actual)
                updated++
            }
        }
        log.info("스토리지 정합 완료: 보정 {}명", updated)
        return JobOutcome.success(updated, mapOf("usersWithPhotos" to sums.size))
    }
}
