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

    
    /**
     * DB JPA 계층 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @return DB JPA 계층 처리 결과
     */
    public static TcEqpSocketEntity newEntity(Long eqpKey) {
        if (eqpKey == null) {
            throw new IllegalArgumentException("eqpKey must not be null");
        }
        TcEqpSocketEntity e = new TcEqpSocketEntity();
        e.setEqpKey(eqpKey);
        return e;
    }

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     */
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
    public String getSocketProtocolType() {
        return socketProtocolType;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param socketProtocolType 통신 채널/세션 정보
     */
    public void setSocketProtocolType(String socketProtocolType) {
        this.socketProtocolType = socketProtocolType;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getConnectionMode() {
        return connectionMode;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param connectionMode 통신 채널/세션 정보
     */
    public void setConnectionMode(String connectionMode) {
        this.connectionMode = connectionMode;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getCharset() {
        return charset;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param charset DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setCharset(String charset) {
        this.charset = charset;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return 처리 성공 여부
     */
    public Boolean getHeartbeatEnabled() {
        return heartbeatEnabled;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param heartbeatEnabled DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setHeartbeatEnabled(Boolean heartbeatEnabled) {
        this.heartbeatEnabled = heartbeatEnabled;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public Integer getHeartbeatInterval() {
        return heartbeatInterval;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param heartbeatInterval 시간 관련 설정 값
     */
    public void setHeartbeatInterval(Integer heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public Integer getReadTimeout() {
        return readTimeout;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param readTimeout 시간 관련 설정 값
     */
    public void setReadTimeout(Integer readTimeout) {
        this.readTimeout = readTimeout;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public Integer getWriteTimeout() {
        return writeTimeout;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param writeTimeout 시간 관련 설정 값
     */
    public void setWriteTimeout(Integer writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public Integer getMaxFrameSizeBytes() {
        return maxFrameSizeBytes;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param maxFrameSizeBytes 처리할 원본 데이터
     */
    public void setMaxFrameSizeBytes(Integer maxFrameSizeBytes) {
        this.maxFrameSizeBytes = maxFrameSizeBytes;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return 처리 성공 여부
     */
    public Boolean getKeepAliveEnabled() {
        return keepAliveEnabled;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param keepAliveEnabled DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setKeepAliveEnabled(Boolean keepAliveEnabled) {
        this.keepAliveEnabled = keepAliveEnabled;
    }
}
