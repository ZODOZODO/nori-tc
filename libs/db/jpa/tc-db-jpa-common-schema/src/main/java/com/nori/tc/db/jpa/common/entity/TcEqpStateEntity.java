package com.nori.tc.db.jpa.common.entity;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.ControlState;
import com.nori.tc.db.domain.common.EqpState;
import com.nori.tc.db.jpa.common.entity.base.AbstractUpdatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * tc_eqp_state 테이블 매핑 엔티티.
 *
 * [DB 스키마]
 * - eqp_key       : bigint PK (tc_eqp FK, ON DELETE CASCADE)
 * - control_state : varchar(20) NULL
 * - eqp_state     : varchar(20) NULL
 * - since_at      : timestamptz NULL
 * - reason_code   : varchar(50) NULL
 * - reason_detail : text NULL
 * - updated_at    : timestamptz NOT NULL (AbstractUpdatedEntity 상속)
 *
 * [설계 포인트]
 * 1. MapStruct 호환성:
 * - public 생성자를 통해 Mapper 접근성을 보장합니다.
 *
 * 2. nullable 컬럼 유지:
 * - sinceAt/controlState/eqpState는 NULL 허용이므로 별도 기본값 로직을 두지 않습니다.
 */
@Entity
@Table(name = "tc_eqp_state")
public class TcEqpStateEntity extends AbstractUpdatedEntity {

    @Id
    @Column(name = "eqp_key", nullable = false)
    private Long eqpKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "control_state", length = 20)
    private ControlState controlState;

    @Enumerated(EnumType.STRING)
    @Column(name = "eqp_state", length = 20)
    private EqpState eqpState;

    @Column(name = "since_at")
    private OffsetDateTime sinceAt;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @Column(name = "reason_detail", columnDefinition = "text")
    private String reasonDetail;

    // =========================================================================
    // Constructors (MapStruct & JPA)
    // =========================================================================

    /**
     * 기본 생성자 (필수)
     */
    public TcEqpStateEntity() {
    }

    /**
     * 전체 인자 생성자
     */
    public TcEqpStateEntity(Long eqpKey, ControlState controlState, EqpState eqpState,
                            OffsetDateTime sinceAt, String reasonCode, String reasonDetail) {
        this.eqpKey = eqpKey;
        this.controlState = controlState;
        this.eqpState = eqpState;
        this.sinceAt = sinceAt;
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
    }

    // =========================================================================
    // Static Factory
    // =========================================================================

    public static TcEqpStateEntity newEntity(long eqpKey) {
        TcEqpStateEntity e = new TcEqpStateEntity();
        e.setEqpKey(eqpKey);
        return e;
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    public Long getEqpKey() {
        return eqpKey;
    }

    public void setEqpKey(Long eqpKey) {
        this.eqpKey = eqpKey;
    }

    public ControlState getControlState() {
        return controlState;
    }

    public void setControlState(ControlState controlState) {
        this.controlState = controlState;
    }

    public EqpState getEqpState() {
        return eqpState;
    }

    public void setEqpState(EqpState eqpState) {
        this.eqpState = eqpState;
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
