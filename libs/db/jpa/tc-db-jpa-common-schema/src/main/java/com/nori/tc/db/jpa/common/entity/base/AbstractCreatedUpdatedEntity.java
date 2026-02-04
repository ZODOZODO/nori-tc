package com.nori.tc.db.jpa.common.entity.base;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * created_at + updated_at 컬럼을 공통 처리하기 위한 베이스 클래스.
 *
 * 주의(중요):
 * - DB에 default now()가 있더라도, JPA가 null을 명시적으로 INSERT 하면 DB default가 적용되지 않을 수 있습니다.
 * - 따라서 애플리케이션 레벨에서 값 세팅을 보장하는 방식이 안정적입니다.
 *
 * 시간대:
 * - hibernate.jdbc.time_zone=UTC 설정을 사용할 예정이므로,
 *   JDBC 입출력 기준은 UTC로 정규화됩니다.
 */
@MappedSuperclass
public abstract class AbstractCreatedUpdatedEntity {

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        final OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        // 생성 시점에도 updated_at은 같이 세팅
        this.updatedAt = (this.updatedAt == null) ? now : this.updatedAt;
    }

    @PreUpdate
    protected void onUpdate() {
        // 갱신 시점에는 무조건 현재 시각으로 갱신하는 쪽이 일반적으로 안전합니다.
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    // 필요 시(특수 마이그레이션)만 setter를 열고, 일반 CRUD에서는 사용하지 않는 것을 권장합니다.
    protected void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    protected void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
