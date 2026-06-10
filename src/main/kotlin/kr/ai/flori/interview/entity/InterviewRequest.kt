package kr.ai.flori.interview.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity

/**
 * 유저 인터뷰 참여 신청 1건. 인증/테넌시와 무관한 공개 모집 데이터.
 * phone은 정규화(숫자만)하여 저장하며 UNIQUE로 중복 신청을 막는다.
 * name·phone 모두 필수.
 */
@Entity
@Table(name = "interview_requests")
class InterviewRequest(
    @Column(name = "name", nullable = false)
    var name: String,
    @Column(name = "phone", nullable = false, unique = true)
    var phone: String,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}
