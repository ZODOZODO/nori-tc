package com.nori.tc.db.jpa.common.entity.model;

import com.nori.tc.db.domain.common.model.ModelStatus;
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

    
    /**
     * DB JPA 계층 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelName 도메인 데이터 객체
     * @param modelVersion 도메인 데이터 객체
     * @return DB JPA 계층 처리 결과
     */
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

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public Long getModelKey() {
        return modelKey;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     */
    public void setModelKey(Long modelKey) {
        this.modelKey = modelKey;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getModelName() {
        return modelName;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelName 도메인 데이터 객체
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getModelVersion() {
        return modelVersion;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersion 도메인 데이터 객체
     */
    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public ProtocolType getCommInterface() {
        return commInterface;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param commInterface DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setCommInterface(ProtocolType commInterface) {
        this.commInterface = commInterface;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public ModelStatus getStatus() {
        return status;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param status DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setStatus(ModelStatus status) {
        this.status = status;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getMaker() {
        return maker;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param maker DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setMaker(String maker) {
        this.maker = maker;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getCreatedBy() {
        return createdBy;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param createdBy DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getUpdatedBy() {
        return updatedBy;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param updatedBy DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
