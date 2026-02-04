package com.nori.tc.db.jpa.common.entity;

import java.time.OffsetDateTime;

import com.nori.tc.db.jpa.common.entity.base.AbstractUpdatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * tc_eqp_oper_state 테이블 매핑 엔티티.
 *
 * [DB 스키마]
 * - eqp_id        : varchar(64) PK (tc_eqp FK, ON DELETE CASCADE)
 * - oper_state    : varchar(32) NOT NULL
 * - since_at      : timestamptz NOT NULL default now()
 * - reason_code   : varchar(64) NULL
 * - reason_detail : text NULL
 * - updated_at    : timestamptz NOT NULL
 *
 * [설계 포인트]
 * 1. MapStruct 호환성:
 * - public 생성자를 통해 Mapper 접근성을 보장합니다.
 *
 * 2. 안전한 기본값 처리:
 * - sinceAt은 필수 값이므로 @PrePersist를 통해 null 방어 로직을 수행합니다.
 */
@Entity
@Table(name = "tc_eqp_oper_state")
public class TcEqpOperStateEntity extends AbstractUpdatedEntity {

    @Id
    @Column(name = "eqp_id", length = 64, nullable = false)
    private String eqpId;

    @Column(name = "oper_state", length = 32, nullable = false)
    private String operState;

    @Column(name = "since_at", nullable = false)
    private OffsetDateTime sinceAt;

    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    @Column(name = "reason_detail", columnDefinition = "text")
    private String reasonDetail;

    // =========================================================================
    // Constructors (MapStruct & JPA)
    // =========================================================================

    /**
     * 기본 생성자 (필수)
     */
    public TcEqpOperStateEntity() {
    }

    /**
     * 전체 인자 생성자
     */
    public TcEqpOperStateEntity(String eqpId, String operState, OffsetDateTime sinceAt, String reasonCode, String reasonDetail) {
        this.eqpId = eqpId;
        this.operState = operState;
        this.sinceAt = sinceAt;
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
    }

    // =========================================================================
    // Static Factory & Lifecycle
    // =========================================================================

    public static TcEqpOperStateEntity newEntity(String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        TcEqpOperStateEntity e = new TcEqpOperStateEntity();
        e.setEqpId(eqpId);
        return e;
    }

    @PrePersist
    private void applyDefaults() {
        if (this.sinceAt == null) {
            this.sinceAt = OffsetDateTime.now();
        }
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    public String getEqpId() {
        return eqpId;
    }

    public void setEqpId(String eqpId) {
        this.eqpId = eqpId;
    }

    public String getOperState() {
        return operState;
    }

    public void setOperState(String operState) {
        this.operState = operState;
    }

    public OffsetDateTime getSinceAt() {
        return sinceAt;
    }

    public void setSinceAt(OffsetDateTime sinceAt) {
        this.sinceAt = sinceAt;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReasonDetail() {
        return reasonDetail;
    }

    public void setReasonDetail(String reasonDetail) {
        this.reasonDetail = reasonDetail;
    }
}