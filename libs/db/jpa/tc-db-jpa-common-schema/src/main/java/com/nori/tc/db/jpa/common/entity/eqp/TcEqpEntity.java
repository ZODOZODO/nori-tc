package com.nori.tc.db.jpa.common.entity.eqp;

import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.jpa.common.entity.base.AbstractCreatedUpdatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * tc_eqp 테이블 매핑 엔티티.
 *
 * [DB 스키마]
 * - eqp_key       : bigint PK (IDENTITY)
 * - eqp_id        : varchar(64) NOT NULL (UNIQUE)
 * - comm_interface: varchar(16) NOT NULL
 * - eqp_ip        : varchar(45) NOT NULL
 * - eqp_port      : int NOT NULL
 * - model_key     : bigint NOT NULL
 * - enabled       : boolean NOT NULL default true
 * - created_at    : timestamptz NOT NULL
 * - updated_at    : timestamptz NOT NULL
 * - created_by    : varchar(50) NOT NULL default 'SYSTEM'
 * - updated_by    : varchar(50) NOT NULL default 'SYSTEM'
 *
 * [설계 포인트]
 * 1. MapStruct 호환성:
 * - MapStruct가 객체를 생성(new)하고 값을 주입할 수 있도록 'public 기본 생성자'와 'public 전체 인자 생성자'를 제공합니다.
 *
 * 2. 안전한 기본값 처리:
 * - DB에 default 값이 있어도, 애플리케이션에서 null을 넣으면 오류가 날 수 있습니다.
 * - @PrePersist를 통해 insert 전 null 방어 로직을 수행합니다.
 */
@Entity
@Table(name = "tc_eqp")
public class TcEqpEntity extends AbstractCreatedUpdatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "eqp_key", nullable = false)
    private Long eqpKey;

    @Column(name = "eqp_id", length = 64, nullable = false, unique = true)
    private String eqpId;

    @Enumerated(EnumType.STRING)
    @Column(name = "comm_interface", length = 16, nullable = false)
    private ProtocolType commInterface;

    @Column(name = "eqp_ip", length = 45, nullable = false)
    private String eqpIp;

    @Column(name = "eqp_port", nullable = false)
    private Integer eqpPort;

    @Column(name = "model_key", nullable = false)
    private Long modelKey;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "created_by", length = 50, nullable = false)
    private String createdBy;

    @Column(name = "updated_by", length = 50, nullable = false)
    private String updatedBy;

    // =========================================================================
    // Constructors (MapStruct & JPA)
    // =========================================================================

    /**
     * 기본 생성자 (필수)
     * - JPA 프록시 생성용
     * - MapStruct 타겟 객체 생성용 (public 필수)
     */
    public TcEqpEntity() {
    }

    /**
     * 전체 인자 생성자
     * - MapStruct가 Domain -> Entity 변환 시 모든 필드를 한 번에 주입할 때 사용
     * - Store에서 수동으로 객체를 생성해야 할 때 사용
     */
    public TcEqpEntity(Long eqpKey, String eqpId, ProtocolType commInterface, String eqpIp, Integer eqpPort, Long modelKey, Boolean enabled, String createdBy, String updatedBy) {
        this.eqpKey = eqpKey;
        this.eqpId = eqpId;
        this.commInterface = commInterface;
        this.eqpIp = eqpIp;
        this.eqpPort = eqpPort;
        this.modelKey = modelKey;
        this.enabled = enabled;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    // =========================================================================
    // Static Factory & Lifecycle
    // =========================================================================

    /**
     * 신규 엔티티 생성 팩토리
     * - Store 계층에서 upsert 로직 수행 중, 해당 ID가 없을 때 빈 객체(PK만 있는)를 만들 때 사용
     */
    public static TcEqpEntity newEntity(String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        TcEqpEntity e = new TcEqpEntity();
        e.setEqpId(eqpId);
        return e;
    }

    /**
     * DB Insert 전 데이터 보정
     * - enabled가 null이면 true로 강제 설정 (DB Default 준수)
     */
    @PrePersist
    private void applyDefaults() {
        if (this.enabled == null) {
            this.enabled = Boolean.TRUE;
        }
        if (this.createdBy == null || this.createdBy.isBlank()) {
            this.createdBy = "SYSTEM";
        }
        if (this.updatedBy == null || this.updatedBy.isBlank()) {
            this.updatedBy = "SYSTEM";
        }
    }

    @PreUpdate
    private void applyUpdateDefaults() {
        if (this.updatedBy == null || this.updatedBy.isBlank()) {
            this.updatedBy = "SYSTEM";
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

    public String getEqpId() {
        return eqpId;
    }

    public void setEqpId(String eqpId) {
        this.eqpId = eqpId;
    }

    public ProtocolType getCommInterface() {
        return commInterface;
    }

    public void setCommInterface(ProtocolType commInterface) {
        this.commInterface = commInterface;
    }

    public String getEqpIp() {
        return eqpIp;
    }

    public void setEqpIp(String eqpIp) {
        this.eqpIp = eqpIp;
    }

    public Integer getEqpPort() {
        return eqpPort;
    }

    public void setEqpPort(Integer eqpPort) {
        this.eqpPort = eqpPort;
    }

    public Long getModelKey() {
        return modelKey;
    }

    public void setModelKey(Long modelKey) {
        this.modelKey = modelKey;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
