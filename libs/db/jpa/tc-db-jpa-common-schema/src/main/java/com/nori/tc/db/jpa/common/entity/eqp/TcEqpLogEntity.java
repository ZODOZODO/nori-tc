package com.nori.tc.db.jpa.common.entity.eqp;

import com.nori.tc.db.domain.common.eqp.LogLevel;
import com.nori.tc.db.jpa.common.entity.base.AbstractUpdatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * tc_eqp_log 테이블 매핑 엔티티.
 *
 * [DB 스키마]
 * - eqp_key            : bigint PK (tc_eqp FK, ON DELETE CASCADE)
 * - log_level          : varchar(10) NOT NULL (TRACE/DEBUG/INFO/WARN/ERROR)
 * - log_retention_days : int NOT NULL default 30 (>= 1)
 * - log_path           : varchar(1000) NULL
 * - updated_at         : timestamptz NOT NULL
 *
 * [설계 포인트]
 * 1. MapStruct 호환성:
 * - public 생성자를 제공하여 Mapper가 객체를 생성할 수 있게 합니다.
 *
 * 2. 기본값 방어:
 * - log_level/log_retention_days가 null이면 DB 기본값에 맞게 보정합니다.
 */
@Entity
@Table(name = "tc_eqp_log")
public class TcEqpLogEntity extends AbstractUpdatedEntity {

    @Id
    @Column(name = "eqp_key", nullable = false)
    private Long eqpKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_level", length = 10, nullable = false)
    private LogLevel logLevel;

    @Column(name = "log_retention_days", nullable = false)
    private Integer logRetentionDays;

    @Column(name = "log_path", length = 1000)
    private String logPath;

    // =========================================================================
    // Constructors (MapStruct & JPA)
    // =========================================================================

    /**
     * 기본 생성자 (필수)
     */
    public TcEqpLogEntity() {
    }

    /**
     * 전체 인자 생성자
     */
    public TcEqpLogEntity(Long eqpKey, LogLevel logLevel, Integer logRetentionDays, String logPath) {
        this.eqpKey = eqpKey;
        this.logLevel = logLevel;
        this.logRetentionDays = logRetentionDays;
        this.logPath = logPath;
    }

    // =========================================================================
    // Static Factory
    // =========================================================================

    public static TcEqpLogEntity newEntity(Long eqpKey) {
        if (eqpKey == null) {
            throw new IllegalArgumentException("eqpKey must not be null");
        }
        TcEqpLogEntity e = new TcEqpLogEntity();
        e.setEqpKey(eqpKey);
        return e;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * DB Insert 전 기본값 방어
     * - logLevel/logRetentionDays가 null이면 DB 기본값에 맞게 보정합니다.
     */
    @PrePersist
    private void applyDefaults() {
        if (this.logLevel == null) {
            this.logLevel = LogLevel.INFO;
        }
        if (this.logRetentionDays == null) {
            this.logRetentionDays = 30;
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

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public Integer getLogRetentionDays() {
        return logRetentionDays;
    }

    public void setLogRetentionDays(Integer logRetentionDays) {
        this.logRetentionDays = logRetentionDays;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }
}
