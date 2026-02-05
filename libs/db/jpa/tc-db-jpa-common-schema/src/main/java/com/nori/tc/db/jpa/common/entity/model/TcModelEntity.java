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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * tc_model 테이블 매핑 엔티티.
 *
 * [DB 스키마]
 * - model_key     : bigint generated always as identity (PK)
 * - model_name    : varchar(128) NOT NULL
 * - model_version : varchar(32) NOT NULL
 * - protocol_type : varchar(16) NOT NULL (HSMS/SOCKET)
 * - status        : varchar(16) NOT NULL (DRAFT/ACTIVE/DEPRECATED)
 * - Constraints   : UNIQUE (model_name, model_version)
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
    @Column(name = "protocol_type", length = 16, nullable = false)
    private ProtocolType protocolType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private ModelStatus status;

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
    public TcModelEntity(Long modelKey, String modelName, String modelVersion, ProtocolType protocolType, ModelStatus status) {
        this.modelKey = modelKey;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.protocolType = protocolType;
        this.status = status;
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

    public ProtocolType getProtocolType() {
        return protocolType;
    }

    public void setProtocolType(ProtocolType protocolType) {
        this.protocolType = protocolType;
    }

    public ModelStatus getStatus() {
        return status;
    }

    public void setStatus(ModelStatus status) {
        this.status = status;
    }
}