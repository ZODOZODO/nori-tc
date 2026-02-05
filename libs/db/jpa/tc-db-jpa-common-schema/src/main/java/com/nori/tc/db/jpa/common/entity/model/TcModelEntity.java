package com.nori.tc.db.jpa.common.entity.model;

import com.nori.tc.db.domain.common.ModelStatus;
import com.nori.tc.db.domain.common.ProtocolType;
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
import jakarta.persistence.UniqueConstraint;

/**
 * tc_model 테이블 매핑 엔티티.
 *
 * [DB 스키마]
 * - model_key      : bigint generated always as identity (PK)
 * - model_name     : varchar(128) NOT NULL
 * - model_version  : varchar(32) NOT NULL
 * - comm_interface : varchar(16) NOT NULL (HSMS/SOCKET)
 * - status         : varchar(16) NOT NULL (DRAFT/ACTIVE/DEPRECATED)
 * - maker          : varchar(32)
 * - created_at     : timestamptz NOT NULL
 * - updated_at     : timestamptz NOT NULL
 * - created_by     : varchar(50) NOT NULL default 'SYSTEM'
 * - updated_by     : varchar(50) NOT NULL default 'SYSTEM'
 * - Constraints    : UNIQUE (model_name, model_version)
 *
 * [설계 포인트]
 * 1. MapStruct 호환성:
 * - public 생성자를 제공하여 Mapper가 객체를 생성할 수 있게 합니다.
 *
 * 2. Identity Strategy:
 * - PK(model_key)는 DB 자동 생성(Identity)을 따릅니다.
 */
@Entity
@Table(
        name = "tc_model",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tc_model_name_version", columnNames = {"model_name", "model_version"})
        }
)
public class TcModelEntity extends AbstractCreatedUpdatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "model_key", nullable = false)
    private Long modelKey;

    @Column(name = "model_name", length = 128, nullable = false)
    private String modelName;

    @Column(name = "model_version", length = 32, nullable = false)
    private String modelVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "comm_interface", length = 16, nullable = false)
    private ProtocolType commInterface;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private ModelStatus status;

    @Column(name = "maker", length = 32)
    private String maker;

    @Column(name = "created_by", length = 50, nullable = false)
    private String createdBy;

    @Column(name = "updated_by", length = 50, nullable = false)
    private String updatedBy;

    // =========================================================================
    // Constructors (MapStruct & JPA)
    // =========================================================================

    /**
     * 기본 생성자 (필수)
     */
    public TcModelEntity() {
    }

    /**
     * 전체 인자 생성자
     */
    public TcModelEntity(Long modelKey, String modelName, String modelVersion, ProtocolType commInterface, ModelStatus status, String maker, String createdBy, String updatedBy) {
        this.modelKey = modelKey;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.commInterface = commInterface;
        this.status = status;
        this.maker = maker;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    // =========================================================================
    // Static Factory
    // =========================================================================

    public static TcModelEntity newEntity(String modelName, String modelVersion) {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be null/blank");
        }
        if (modelVersion == null || modelVersion.isBlank()) {
            throw new IllegalArgumentException("modelVersion must not be null/blank");
        }
        TcModelEntity e = new TcModelEntity();
        e.setModelName(modelName);
        e.setModelVersion(modelVersion);
        return e;
    }

    /**
     * DB Insert 전 데이터 보정
     * - created_by/updated_by가 비어있으면 SYSTEM으로 기본값 세팅
     */
    @PrePersist
    private void applyDefaults() {
        if (this.createdBy == null || this.createdBy.isBlank()) {
            this.createdBy = "SYSTEM";
        }
        if (this.updatedBy == null || this.updatedBy.isBlank()) {
            this.updatedBy = "SYSTEM";
        }
    }

    /**
     * DB Update 전 데이터 보정
     * - updated_by가 비어있으면 SYSTEM으로 기본값 세팅
     */
    @PreUpdate
    private void applyUpdateDefaults() {
        if (this.updatedBy == null || this.updatedBy.isBlank()) {
            this.updatedBy = "SYSTEM";
        }
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    public Long getModelKey() {
        return modelKey;
    }

    public void setModelKey(Long modelKey) {
        this.modelKey = modelKey;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public ProtocolType getCommInterface() {
        return commInterface;
    }

    public void setCommInterface(ProtocolType commInterface) {
        this.commInterface = commInterface;
    }

    public ModelStatus getStatus() {
        return status;
    }

    public void setStatus(ModelStatus status) {
        this.status = status;
    }

    public String getMaker() {
        return maker;
    }

    public void setMaker(String maker) {
        this.maker = maker;
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
