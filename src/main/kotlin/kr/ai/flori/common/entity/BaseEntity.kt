package kr.ai.flori.common.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

/**
 * 생성 시각만 자동 관리하는 엔티티 공통 베이스(append-only/이력 엔티티 등).
 *
 * `createdAt`은 Hibernate가 INSERT 시 자동 기록한다(@CreationTimestamp).
 * 서비스/엔티티가 수동으로 set하지 않는다 — 중복 제거 + 누락 방지.
 */
@MappedSuperclass
abstract class BaseCreatedEntity {
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

/**
 * 생성/수정 시각을 모두 자동 관리하는 엔티티 공통 베이스.
 *
 * `updatedAt`은 Hibernate가 INSERT/UPDATE 시 자동 갱신한다(@UpdateTimestamp).
 * 따라서 서비스는 `entity.updatedAt = Instant.now()`를 직접 호출하지 않는다.
 */
@MappedSuperclass
abstract class BaseEntity : BaseCreatedEntity() {
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
