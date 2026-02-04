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
 * - eqp_id               : varchar(64) PK (tc_eqp FK, ON DELETE CASCADE)
 * - device_id            : int NOT NULL
 * - t3_ms ~ t8_ms        : int NOT NULL (CHECK > 0)
 * - linktest_enabled     : boolean NOT NULL default true
 * - linktest_interval_ms : int NOT NULL (CHECK > 0)
 * - max_msg_bytes        : int NOT NULL (CHECK > 0)
 * - created_at           : timestamptz NOT NULL
 * - updated_at           : timestamptz NOT NULL
 *
 * [설계 포인트]
 * 1. MapStruct 호환성:
 * - MapStruct/Store에서 객체를 생성하고 값을 주입할 수 있도록 public 생성자들을 제공합니다.
 *
 * 2. 안전한 기본값 처리:
 * - linktest_enabled는 DB Default가 true이므로, null 유입 시 true로 보정합니다.
 */
@Entity
@Table(name = "tc_eqp_hsms")
public class TcEqpHsmsEntity extends AbstractCreatedUpdatedEntity {

    @Id
    @Column(name = "eqp_id", length = 64, nullable = false)
    private String eqpId;

    @Column(name = "device_id", nullable = false)
    private Integer deviceId;

    @Column(name = "t3_ms", nullable = false)
    private Integer t3Ms;

    @Column(name = "t5_ms", nullable = false)
    private Integer t5Ms;

    @Column(name = "t6_ms", nullable = false)
    private Integer t6Ms;

    @Column(name = "t7_ms", nullable = false)
    private Integer t7Ms;

    @Column(name = "t8_ms", nullable = false)
    private Integer t8Ms;

    @Column(name = "linktest_enabled", nullable = false)
    private Boolean linktestEnabled;

    @Column(name = "linktest_interval_ms", nullable = false)
    private Integer linktestIntervalMs;

    @Column(name = "max_msg_bytes", nullable = false)
    private Integer maxMsgBytes;

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
    public TcEqpHsmsEntity(String eqpId, Integer deviceId, Integer t3Ms, Integer t5Ms, Integer t6Ms,
                           Integer t7Ms, Integer t8Ms, Boolean linktestEnabled,
                           Integer linktestIntervalMs, Integer maxMsgBytes) {
        this.eqpId = eqpId;
        this.deviceId = deviceId;
        this.t3Ms = t3Ms;
        this.t5Ms = t5Ms;
        this.t6Ms = t6Ms;
        this.t7Ms = t7Ms;
        this.t8Ms = t8Ms;
        this.linktestEnabled = linktestEnabled;
        this.linktestIntervalMs = linktestIntervalMs;
        this.maxMsgBytes = maxMsgBytes;
    }

    // =========================================================================
    // Static Factory & Lifecycle
    // =========================================================================

    /**
     * 신규 엔티티 생성 팩토리
     * - Store 계층에서 upsert 로직 수행 중, 해당 ID가 없을 때 사용
     */
    public static TcEqpHsmsEntity newEntity(String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        TcEqpHsmsEntity e = new TcEqpHsmsEntity();
        e.setEqpId(eqpId);
        return e;
    }

    /**
     * DB Insert 전 데이터 보정
     * - linktest_enabled: null이면 true로 설정 (DB Default 준수)
     */
    @PrePersist
    private void applyDefaults() {
        if (this.linktestEnabled == null) {
            this.linktestEnabled = Boolean.TRUE;
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

    public Integer getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Integer deviceId) {
        this.deviceId = deviceId;
    }

    public Integer getT3Ms() {
        return t3Ms;
    }

    public void setT3Ms(Integer t3Ms) {
        this.t3Ms = t3Ms;
    }

    public Integer getT5Ms() {
        return t5Ms;
    }

    public void setT5Ms(Integer t5Ms) {
        this.t5Ms = t5Ms;
    }

    public Integer getT6Ms() {
        return t6Ms;
    }

    public void setT6Ms(Integer t6Ms) {
        this.t6Ms = t6Ms;
    }

    public Integer getT7Ms() {
        return t7Ms;
    }

    public void setT7Ms(Integer t7Ms) {
        this.t7Ms = t7Ms;
    }

    public Integer getT8Ms() {
        return t8Ms;
    }

    public void setT8Ms(Integer t8Ms) {
        this.t8Ms = t8Ms;
    }

    public Boolean getLinktestEnabled() {
        return linktestEnabled;
    }

    public void setLinktestEnabled(Boolean linktestEnabled) {
        this.linktestEnabled = linktestEnabled;
    }

    public Integer getLinktestIntervalMs() {
        return linktestIntervalMs;
    }

    public void setLinktestIntervalMs(Integer linktestIntervalMs) {
        this.linktestIntervalMs = linktestIntervalMs;
    }

    public Integer getMaxMsgBytes() {
        return maxMsgBytes;
    }

    public void setMaxMsgBytes(Integer maxMsgBytes) {
        this.maxMsgBytes = maxMsgBytes;
    }
}