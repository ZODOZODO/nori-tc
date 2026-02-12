package com.nori.tc.db.jpa.common.entity.base;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * updated_at 컬럼만 공통 처리하기 위한 베이스 클래스.
 */
@MappedSuperclass
public abstract class AbstractUpdatedEntity {

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    
    /**
     * DB JPA 계층 감사/상태 필드를 최신 값으로 갱신합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     */
    @PrePersist
    protected void onCreate() {
        if (this.updatedAt == null) {
            this.updatedAt = OffsetDateTime.now();
        }
    }

    
    /**
     * DB JPA 계층 감사/상태 필드를 최신 값으로 갱신합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param updatedAt DB JPA 계층 처리에 사용하는 입력 값
     */
    protected void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
