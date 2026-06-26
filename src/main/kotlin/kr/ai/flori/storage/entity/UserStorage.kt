package kr.ai.flori.storage.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity

/**
 * 인당 갤러리 스토리지 사용량 카운터. (user_id) 당 1행(UNIQUE). 행이 없으면 기본 한도로 get-or-create.
 * 사용량은 presign 검사·카드 저장 시 [usedBytes] 원자 증감, @Scheduled 정합으로 DB 합과 맞춘다.
 * 멀티테넌시: user_id 격리.
 */
@Entity
@Table(name = "user_storage")
class UserStorage(
    @Column(name = "user_id", nullable = false, unique = true)
    var userId: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "used_bytes", nullable = false)
    var usedBytes: Long = 0

    @Column(name = "quota_bytes", nullable = false)
    var quotaBytes: Long = DEFAULT_QUOTA_BYTES

    companion object {
        /** 기본 한도 3GiB. */
        const val DEFAULT_QUOTA_BYTES: Long = 3L * 1024 * 1024 * 1024
    }
}
