package com.nori.tc.db.jpa.common.entity;

import com.nori.tc.db.jpa.common.entity.base.AbstractCreatedUpdatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * tc_eqp_hsms 테이블 매핑 엔티티.
 *
 * [DB 스키마]
 * - eqp_key            : bigint PK/FK (tc_eqp FK, ON DELETE CASCADE)
 * - device_id          : int NOT NULL
 * - connection_mode    : varchar(10) NOT NULL (ACTIVE/PASSIVE)
 * - t3_timeout ~ t8_timeout : int NOT NULL (CHECK > 0)
 * - link_test_enabled  : boolean NOT NULL default true
 * - link_test_interval : int NOT NULL (CHECK > 0)
 * - max_msg_bytes      : bigint NOT NULL (CHECK > 0)
 * - created_at         : timestamptz NOT NULL
 * - updated_at         : timestamptz NOT NULL
 *
 * [설계 포인트]
 * 1. MapStruct 호환성:
 * - MapStruct/Store에서 객체를 생성하고 값을 주입할 수 있도록 public 생성자들을 제공합니다.
 *
 * 2. 안전한 기본값 처리:
 * - link_test_enabled는 DB Default가 true이므로, null 유입 시 true로 보정합니다.
 * - t3~t8, link_test_interval, max_msg_bytes는 DB Default가 있으므로 null 유입 시 기본값으로 보정합니다.
 */
@Entity
@Table(name = "tc_eqp_hsms")
public class TcEqpHsmsEntity extends AbstractCreatedUpdatedEntity {

    @Id
    @Column(name = "eqp_key", nullable = false)
    private Long eqpKey;

    @Column(name = "device_id", nullable = false)
    private Integer deviceId;

    @Column(name = "connection_mode", length = 10, nullable = false)
    private String connectionMode;

    @Column(name = "t3_timeout", nullable = false)
    private Integer t3Timeout;

    @Column(name = "t5_timeout", nullable = false)
    private Integer t5Timeout;

    @Column(name = "t6_timeout", nullable = false)
    private Integer t6Timeout;

    @Column(name = "t7_timeout", nullable = false)
    private Integer t7Timeout;

    @Column(name = "t8_timeout", nullable = false)
    private Integer t8Timeout;

    @Column(name = "link_test_enabled", nullable = false)
    private Boolean linkTestEnabled;

    @Column(name = "link_test_interval", nullable = false)
    private Integer linkTestInterval;

    @Column(name = "max_msg_bytes", nullable = false)
    private Long maxMsgBytes;

    // =========================================================================
    // Constructors (MapStruct & JPA)
    // =========================================================================

    /**
     * 기본 생성자 (필수)
     * - JPA 프록시 생성용
     * - MapStruct 타겟 객체 생성용 (public 필수)
     */
    public TcEqpHsmsEntity() {
    }

    /**
     * 전체 인자 생성자
     * - MapStruct가 Domain -> Entity 변환 시 모든 필드를 한 번에 주입할 때 사용
     */
    public TcEqpHsmsEntity(Long eqpKey, Integer deviceId, String connectionMode, Integer t3Timeout, Integer t5Timeout,
                           Integer t6Timeout, Integer t7Timeout, Integer t8Timeout, Boolean linkTestEnabled,
                           Integer linkTestInterval, Long maxMsgBytes) {
        this.eqpKey = eqpKey;
        this.deviceId = deviceId;
        this.connectionMode = connectionMode;
        this.t3Timeout = t3Timeout;
        this.t5Timeout = t5Timeout;
        this.t6Timeout = t6Timeout;
        this.t7Timeout = t7Timeout;
        this.t8Timeout = t8Timeout;
        this.linkTestEnabled = linkTestEnabled;
        this.linkTestInterval = linkTestInterval;
        this.maxMsgBytes = maxMsgBytes;
    }

    // =========================================================================
    // Static Factory & Lifecycle
    // =========================================================================

    /**
     * 신규 엔티티 생성 팩토리
     * - Store 계층에서 upsert 로직 수행 중, 해당 ID가 없을 때 사용
     */
    public static TcEqpHsmsEntity newEntity(long eqpKey) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        TcEqpHsmsEntity e = new TcEqpHsmsEntity();
        e.setEqpKey(eqpKey);
        return e;
    }

    /**
     * DB Insert 전 데이터 보정
     * - link_test_enabled: null이면 true로 설정 (DB Default 준수)
     */
    @PrePersist
    private void applyDefaults() {
        if (this.t3Timeout == null) {
            this.t3Timeout = 45;
        }
        if (this.t5Timeout == null) {
            this.t5Timeout = 10;
        }
        if (this.t6Timeout == null) {
            this.t6Timeout = 5;
        }
        if (this.t7Timeout == null) {
            this.t7Timeout = 10;
        }
        if (this.t8Timeout == null) {
            this.t8Timeout = 5;
        }
        if (this.linkTestEnabled == null) {
            this.linkTestEnabled = Boolean.TRUE;
        }
        if (this.linkTestInterval == null) {
            this.linkTestInterval = 60;
        }
        if (this.maxMsgBytes == null) {
            this.maxMsgBytes = 10_485_760L;
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

    public Integer getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Integer deviceId) {
        this.deviceId = deviceId;
    }

    public String getConnectionMode() {
        return connectionMode;
    }

    public void setConnectionMode(String connectionMode) {
        this.connectionMode = connectionMode;
    }

    public Integer getT3Timeout() {
        return t3Timeout;
    }

    public void setT3Timeout(Integer t3Timeout) {
        this.t3Timeout = t3Timeout;
    }

    public Integer getT5Timeout() {
        return t5Timeout;
    }

    public void setT5Timeout(Integer t5Timeout) {
        this.t5Timeout = t5Timeout;
    }

    public Integer getT6Timeout() {
        return t6Timeout;
    }

    public void setT6Timeout(Integer t6Timeout) {
        this.t6Timeout = t6Timeout;
    }

    public Integer getT7Timeout() {
        return t7Timeout;
    }

    public void setT7Timeout(Integer t7Timeout) {
        this.t7Timeout = t7Timeout;
    }

    public Integer getT8Timeout() {
        return t8Timeout;
    }

    public void setT8Timeout(Integer t8Timeout) {
        this.t8Timeout = t8Timeout;
    }

    public Boolean getLinkTestEnabled() {
        return linkTestEnabled;
    }

    public void setLinkTestEnabled(Boolean linkTestEnabled) {
        this.linkTestEnabled = linkTestEnabled;
    }

    public Integer getLinkTestInterval() {
        return linkTestInterval;
    }

    public void setLinkTestInterval(Integer linkTestInterval) {
        this.linkTestInterval = linkTestInterval;
    }

    public Long getMaxMsgBytes() {
        return maxMsgBytes;
    }

    public void setMaxMsgBytes(Long maxMsgBytes) {
        this.maxMsgBytes = maxMsgBytes;
    }
}
