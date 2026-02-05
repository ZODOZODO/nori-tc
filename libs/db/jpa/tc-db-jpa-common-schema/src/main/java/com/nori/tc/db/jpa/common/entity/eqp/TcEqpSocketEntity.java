package com.nori.tc.db.jpa.common.entity.eqp;

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
 * - eqp_key               : bigint PK (tc_eqp FK, ON DELETE CASCADE)
 * - socket_protocol_type  : varchar(32) NOT NULL
 * - connection_mode       : varchar(10) NOT NULL (CHECK: ACTIVE|PASSIVE)
 * - charset               : varchar(20) NOT NULL default 'UTF-8'
 * - heartbeat_enabled     : boolean NOT NULL default true
 * - heartbeat_interval    : int NOT NULL default 30 (CHECK >= 0)
 * - read_timeout          : int NOT NULL default 0 (CHECK >= 0)
 * - write_timeout         : int NOT NULL default 0 (CHECK >= 0)
 * - max_frame_size_bytes  : int NOT NULL default 8192 (CHECK > 0)
 * - keep_alive_enabled    : boolean NOT NULL default true
 * - created_at            : timestamptz NOT NULL
 * - updated_at            : timestamptz NOT NULL
 *
 * [설계 포인트]
 * 1. MapStruct 호환성:
 * - public 생성자를 제공하여 Mapper와의 호환성을 보장합니다.
 *
 * 2. 안전한 기본값 처리:
 * - charset, heartbeat_* 등 DB Default가 있는 컬럼들에 대해 @PrePersist로 null safe 처리를 합니다.
 * - connection_mode는 NOT NULL이며 기본값이 없으므로 애플리케이션 레벨에서 유효성 검증이 필요합니다.
 */
@Entity
@Table(name = "tc_eqp_socket")
public class TcEqpSocketEntity extends AbstractCreatedUpdatedEntity {

    @Id
    @Column(name = "eqp_key", nullable = false)
    private Long eqpKey;

    @Column(name = "socket_protocol_type", length = 32, nullable = false)
    private String socketProtocolType;

    @Column(name = "connection_mode", length = 10, nullable = false)
    private String connectionMode;

    @Column(name = "charset", length = 20, nullable = false)
    private String charset;

    @Column(name = "heartbeat_enabled", nullable = false)
    private Boolean heartbeatEnabled;

    @Column(name = "heartbeat_interval", nullable = false)
    private Integer heartbeatInterval;

    @Column(name = "read_timeout", nullable = false)
    private Integer readTimeout;

    @Column(name = "write_timeout", nullable = false)
    private Integer writeTimeout;

    @Column(name = "max_frame_size_bytes", nullable = false)
    private Integer maxFrameSizeBytes;

    @Column(name = "keep_alive_enabled", nullable = false)
    private Boolean keepAliveEnabled;

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
    public TcEqpSocketEntity(
            Long eqpKey,
            String socketProtocolType,
            String connectionMode,
            String charset,
            Boolean heartbeatEnabled,
            Integer heartbeatInterval,
            Integer readTimeout,
            Integer writeTimeout,
            Integer maxFrameSizeBytes,
            Boolean keepAliveEnabled
    ) {
        this.eqpKey = eqpKey;
        this.socketProtocolType = socketProtocolType;
        this.connectionMode = connectionMode;
        this.charset = charset;
        this.heartbeatEnabled = heartbeatEnabled;
        this.heartbeatInterval = heartbeatInterval;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
        this.maxFrameSizeBytes = maxFrameSizeBytes;
        this.keepAliveEnabled = keepAliveEnabled;
    }

    // =========================================================================
    // Static Factory & Lifecycle
    // =========================================================================

    public static TcEqpSocketEntity newEntity(Long eqpKey) {
        if (eqpKey == null) {
            throw new IllegalArgumentException("eqpKey must not be null");
        }
        TcEqpSocketEntity e = new TcEqpSocketEntity();
        e.setEqpKey(eqpKey);
        return e;
    }

    @PrePersist
    private void applyDefaults() {
        if (this.charset == null || this.charset.isBlank()) {
            this.charset = "UTF-8";
        }
        if (this.heartbeatEnabled == null) {
            this.heartbeatEnabled = Boolean.TRUE;
        }
        if (this.heartbeatInterval == null) {
            this.heartbeatInterval = 30;
        }
        if (this.readTimeout == null) {
            this.readTimeout = 0;
        }
        if (this.writeTimeout == null) {
            this.writeTimeout = 0;
        }
        if (this.maxFrameSizeBytes == null) {
            this.maxFrameSizeBytes = 8192;
        }
        if (this.keepAliveEnabled == null) {
            this.keepAliveEnabled = Boolean.TRUE;
        }
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

    public String getSocketProtocolType() {
        return socketProtocolType;
    }

    public void setSocketProtocolType(String socketProtocolType) {
        this.socketProtocolType = socketProtocolType;
    }

    public String getConnectionMode() {
        return connectionMode;
    }

    public void setConnectionMode(String connectionMode) {
        this.connectionMode = connectionMode;
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

    public Integer getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Integer heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public Integer getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Integer readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Integer getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Integer writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public Integer getMaxFrameSizeBytes() {
        return maxFrameSizeBytes;
    }

    public void setMaxFrameSizeBytes(Integer maxFrameSizeBytes) {
        this.maxFrameSizeBytes = maxFrameSizeBytes;
    }

    public Boolean getKeepAliveEnabled() {
        return keepAliveEnabled;
    }

    public void setKeepAliveEnabled(Boolean keepAliveEnabled) {
        this.keepAliveEnabled = keepAliveEnabled;
    }
}
