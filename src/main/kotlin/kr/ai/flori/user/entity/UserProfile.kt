package kr.ai.flori.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * 사용자 프로필(users와 1:1). PK이자 FK인 user_id를 공유한다(IDENTITY 생성 아님).
 *
 * - storeName(가게명)은 users.nickname(계정 표시명/소셜 닉네임)과 분리해 저장한다.
 * - interests/specialties는 PhotoCard.tags와 동일한 네이티브 TEXT[] 매핑.
 * - 옵션 값(시도/나이대/관심사/주력)의 enum 검증은 웹이 소유 — 서버는 자유 문자열로 저장한다.
 * - 멀티테넌시: PK가 user_id이므로 임의 user_id 주입이 불가능하다(본질적 격리).
 */
@Entity
@Table(name = "user_profiles")
class UserProfile(
    @Id
    @Column(name = "user_id")
    var userId: Long,
    @Column(name = "store_name", nullable = false)
    var storeName: String,
    @Column(name = "phone_number", nullable = false)
    var phoneNumber: String,
    @Column(name = "region_sido", nullable = false)
    var regionSido: String,
) : BaseEntity() {
    @Column(name = "profile_image_url")
    var profileImageUrl: String? = null

    @Column(name = "region_sigungu")
    var regionSigungu: String? = null

    @Column(name = "owner_age_range")
    var ownerAgeRange: String? = null

    // Array<String>(네이티브 ARRAY) — List<String>(JSON)과 다른 자바 타입이라 전역 타입 충돌 회피
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "interests", columnDefinition = "text[]")
    var interests: Array<String> = emptyArray()

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "specialties", columnDefinition = "text[]")
    var specialties: Array<String> = emptyArray()
}
