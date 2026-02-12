package com.nori.tc.db.jpa.common.entity.eqp;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.eqp.ControlState;
import com.nori.tc.db.domain.common.eqp.EqpState;
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

    
    /**
     * DB JPA 계층 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @return DB JPA 계층 처리 결과
     */
    public static TcEqpStateEntity newEntity(long eqpKey) {
        TcEqpStateEntity e = new TcEqpStateEntity();
        e.setEqpKey(eqpKey);
        return e;
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public Long getEqpKey() {
        return eqpKey;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     */
    public void setEqpKey(Long eqpKey) {
        this.eqpKey = eqpKey;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public ControlState getControlState() {
        return controlState;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param controlState DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setControlState(ControlState controlState) {
        this.controlState = controlState;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public EqpState getEqpState() {
        return eqpState;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpState DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setEqpState(EqpState eqpState) {
        this.eqpState = eqpState;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public OffsetDateTime getSinceAt() {
        return sinceAt;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param sinceAt DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setSinceAt(OffsetDateTime sinceAt) {
        this.sinceAt = sinceAt;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getReasonCode() {
        return reasonCode;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param reasonCode DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getReasonDetail() {
        return reasonDetail;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param reasonDetail DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setReasonDetail(String reasonDetail) {
        this.reasonDetail = reasonDetail;
    }
}
