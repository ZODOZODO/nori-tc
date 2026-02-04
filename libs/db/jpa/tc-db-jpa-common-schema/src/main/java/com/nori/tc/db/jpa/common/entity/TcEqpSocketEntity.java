package com.nori.tc.db.jpa.common.entity;

import com.nori.tc.db.jpa.common.entity.base.AbstractCreatedUpdatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * tc_eqp_socket 테이블 매핑 엔티티.
 *
 * [DB 스키마]
 * - eqp_id                : varchar(64) PK (tc_eqp FK, ON DELETE CASCADE)
 * - socket_protocol_type  : varchar(32) NOT NULL
 * - charset               : varchar(32) NOT NULL default 'UTF-8'
 * - heartbeat_enabled     : boolean NOT NULL default false
 * - heartbeat_interval_ms : int NOT NULL default 0 (CHECK >= 0)
 * - created_at            : timestamptz NOT NULL
 * - updated_at            : timestamptz NOT NULL
 *
 * [설계 포인트]
 * 1. MapStruct 호환성:
 * - public 생성자를 제공하여 Mapper와의 호환성을 보장합니다.
 *
 * 2. 안전한 기본값 처리:
 * - charset, heartbeat_* 등 DB Default가 있는 컬럼들에 대해 @PrePersist로 null safe 처리를 합니다.
 */
@Entity
@Table(name = "tc_eqp_socket")
public class TcEqpSocketEntity extends AbstractCreatedUpdatedEntity {

    @Id
    @Column(name = "eqp_id", length = 64, nullable = false)
    private String eqpId;

    @Column(name = "socket_protocol_type", length = 32, nullable = false)
    private String socketProtocolType;

    @Column(name = "charset", length = 32, nullable = false)
    private String charset;

    @Column(name = "heartbeat_enabled", nullable = false)
    private Boolean heartbeatEnabled;

    @Column(name = "heartbeat_interval_ms", nullable = false)
    private Integer heartbeatIntervalMs;

    // =========================================================================
    // Constructors (MapStruct & JPA)
    // =========================================================================

    /**
     * 기본 생성자 (필수)
     */
    public TcEqpSocketEntity() {
    }

    /**
     * 전체 인자 생성자
     */
    public TcEqpSocketEntity(String eqpId, String socketProtocolType, String charset, Boolean heartbeatEnabled, Integer heartbeatIntervalMs) {
        this.eqpId = eqpId;
        this.socketProtocolType = socketProtocolType;
        this.charset = charset;
        this.heartbeatEnabled = heartbeatEnabled;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    // =========================================================================
    // Static Factory & Lifecycle
    // =========================================================================

    public static TcEqpSocketEntity newEntity(String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        TcEqpSocketEntity e = new TcEqpSocketEntity();
        e.setEqpId(eqpId);
        return e;
    }

    @PrePersist
    private void applyDefaults() {
        if (this.charset == null || this.charset.isBlank()) {
            this.charset = "UTF-8";
        }
        if (this.heartbeatEnabled == null) {
            this.heartbeatEnabled = Boolean.FALSE;
        }
        if (this.heartbeatIntervalMs == null) {
            this.heartbeatIntervalMs = 0;
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

    public String getSocketProtocolType() {
        return socketProtocolType;
    }

    public void setSocketProtocolType(String socketProtocolType) {
        this.socketProtocolType = socketProtocolType;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public Boolean getHeartbeatEnabled() {
        return heartbeatEnabled;
    }

    public void setHeartbeatEnabled(Boolean heartbeatEnabled) {
        this.heartbeatEnabled = heartbeatEnabled;
    }

    public Integer getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(Integer heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }
}