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

    
    /**
     * DB JPA 계층 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @return DB JPA 계층 처리 결과
     */
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
    public LogLevel getLogLevel() {
        return logLevel;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param logLevel DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public Integer getLogRetentionDays() {
        return logRetentionDays;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param logRetentionDays DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setLogRetentionDays(Integer logRetentionDays) {
        this.logRetentionDays = logRetentionDays;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getLogPath() {
        return logPath;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param logPath DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }
}
