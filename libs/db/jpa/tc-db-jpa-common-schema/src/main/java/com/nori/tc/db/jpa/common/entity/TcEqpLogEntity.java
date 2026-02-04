package com.nori.tc.db.jpa.common.entity;

import com.nori.tc.db.domain.common.LogLevel;
import com.nori.tc.db.jpa.common.entity.base.AbstractCreatedUpdatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * tc_eqp_log 테이블 매핑 엔티티.
 *
 * [DB 스키마]
 * - eqp_id     : varchar(64) PK (tc_eqp FK, ON DELETE CASCADE)
 * - log_level  : varchar(16) NOT NULL (INFO/DEBUG/TRACE)
 * - log_path   : text NOT NULL
 * - created_at : timestamptz NOT NULL
 * - updated_at : timestamptz NOT NULL
 *
 * [설계 포인트]
 * 1. MapStruct 호환성:
 * - public 생성자를 제공하여 Mapper가 객체를 생성할 수 있게 합니다.
 *
 * 2. 타입 매핑:
 * - log_path는 DB 자료형이 'text'이므로 columnDefinition="text"를 명시합니다.
 */
@Entity
@Table(name = "tc_eqp_log")
public class TcEqpLogEntity extends AbstractCreatedUpdatedEntity {

    @Id
    @Column(name = "eqp_id", length = 64, nullable = false)
    private String eqpId;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_level", length = 16, nullable = false)
    private LogLevel logLevel;

    @Column(name = "log_path", nullable = false, columnDefinition = "text")
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
    public TcEqpLogEntity(String eqpId, LogLevel logLevel, String logPath) {
        this.eqpId = eqpId;
        this.logLevel = logLevel;
        this.logPath = logPath;
    }

    // =========================================================================
    // Static Factory
    // =========================================================================

    public static TcEqpLogEntity newEntity(String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        TcEqpLogEntity e = new TcEqpLogEntity();
        e.setEqpId(eqpId);
        return e;
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

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }
}