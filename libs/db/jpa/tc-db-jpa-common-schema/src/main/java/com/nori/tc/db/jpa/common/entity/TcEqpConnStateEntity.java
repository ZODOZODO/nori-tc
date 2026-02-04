package com.nori.tc.db.jpa.common.entity;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.ConnectionState;
import com.nori.tc.db.jpa.common.entity.base.AbstractUpdatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * tc_eqp_conn_state 테이블 매핑 엔티티.
 *
 * [DB 스키마]
 * - eqp_id             : varchar(64) PK (FK)
 * - conn_state         : varchar(16) NOT NULL
 * - since_at           : timestamptz NOT NULL default now()
 * - last_connect_at    : timestamptz
 * - last_disconnect_at : timestamptz
 * - last_rx_at         : timestamptz
 * - last_tx_at         : timestamptz
 * - last_error_code    : varchar(64)
 * - last_error_message : text
 * - updated_at         : timestamptz NOT NULL (AbstractUpdatedEntity 상속)
 *
 * [설계 포인트]
 * 1. MapStruct 호환성:
 * - MapStruct가 객체를 생성하고 값을 주입할 수 있도록 public 생성자들을 제공합니다.
 *
 * 2. 안전한 기본값 처리:
 * - sinceAt은 로직상 중요하므로 @PrePersist로 null 방어 로직을 수행합니다.
 */
@Entity
@Table(name = "tc_eqp_conn_state")
public class TcEqpConnStateEntity extends AbstractUpdatedEntity {

    @Id
    @Column(name = "eqp_id", length = 64, nullable = false)
    private String eqpId;

    @Enumerated(EnumType.STRING)
    @Column(name = "conn_state", length = 16, nullable = false)
    private ConnectionState connState;

    @Column(name = "since_at", nullable = false)
    private OffsetDateTime sinceAt;

    @Column(name = "last_connect_at")
    private OffsetDateTime lastConnectAt;

    @Column(name = "last_disconnect_at")
    private OffsetDateTime lastDisconnectAt;

    @Column(name = "last_rx_at")
    private OffsetDateTime lastRxAt;

    @Column(name = "last_tx_at")
    private OffsetDateTime lastTxAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "text")
    private String lastErrorMessage;

    // =========================================================================
    // Constructors (MapStruct & JPA)
    // =========================================================================

    /**
     * 기본 생성자 (필수)
     * - JPA 프록시 생성용
     * - MapStruct 타겟 객체 생성용 (public 필수)
     */
    public TcEqpConnStateEntity() {
    }

    /**
     * 전체 인자 생성자
     * - MapStruct가 Domain -> Entity 변환 시 모든 필드를 한 번에 주입할 때 사용
     */
    public TcEqpConnStateEntity(String eqpId, ConnectionState connState, OffsetDateTime sinceAt,
                                OffsetDateTime lastConnectAt, OffsetDateTime lastDisconnectAt,
                                OffsetDateTime lastRxAt, OffsetDateTime lastTxAt,
                                String lastErrorCode, String lastErrorMessage) {
        this.eqpId = eqpId;
        this.connState = connState;
        this.sinceAt = sinceAt;
        this.lastConnectAt = lastConnectAt;
        this.lastDisconnectAt = lastDisconnectAt;
        this.lastRxAt = lastRxAt;
        this.lastTxAt = lastTxAt;
        this.lastErrorCode = lastErrorCode;
        this.lastErrorMessage = lastErrorMessage;
    }

    // =========================================================================
    // Static Factory & Lifecycle
    // =========================================================================

    /**
     * 신규 엔티티 생성 팩토리
     * - Store 계층에서 upsert 로직 수행 중, 해당 ID가 없을 때 사용
     */
    public static TcEqpConnStateEntity newEntity(String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        TcEqpConnStateEntity e = new TcEqpConnStateEntity();
        e.setEqpId(eqpId);
        return e;
    }

    /**
     * DB Insert 전 데이터 보정
     * - sinceAt이 null이면 now()로 설정하여 DB Not Null 제약 준수
     */
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

    public ConnectionState getConnState() {
        return connState;
    }

    public void setConnState(ConnectionState connState) {
        this.connState = connState;
    }

    public OffsetDateTime getSinceAt() {
        return sinceAt;
    }

    public void setSinceAt(OffsetDateTime sinceAt) {
        this.sinceAt = sinceAt;
    }

    public OffsetDateTime getLastConnectAt() {
        return lastConnectAt;
    }

    public void setLastConnectAt(OffsetDateTime lastConnectAt) {
        this.lastConnectAt = lastConnectAt;
    }

    public OffsetDateTime getLastDisconnectAt() {
        return lastDisconnectAt;
    }

    public void setLastDisconnectAt(OffsetDateTime lastDisconnectAt) {
        this.lastDisconnectAt = lastDisconnectAt;
    }

    public OffsetDateTime getLastRxAt() {
        return lastRxAt;
    }

    public void setLastRxAt(OffsetDateTime lastRxAt) {
        this.lastRxAt = lastRxAt;
    }

    public OffsetDateTime getLastTxAt() {
        return lastTxAt;
    }

    public void setLastTxAt(OffsetDateTime lastTxAt) {
        this.lastTxAt = lastTxAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }
}