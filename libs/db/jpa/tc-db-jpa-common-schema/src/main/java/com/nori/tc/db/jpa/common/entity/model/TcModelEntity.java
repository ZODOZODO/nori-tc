package com.nori.tc.db.jpa.common.entity.model;

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
 * {@code tc_model} 원장 테이블 매핑 엔티티입니다.
 *
 * <p>
 * 중요: 모델 버전 정보({@code model_version}, {@code status})는
 * {@code tc_model_version} 테이블에 존재합니다.
 * 따라서 본 엔티티는 원장 테이블의 실 컬럼만 매핑합니다.
 * </p>
 *
 * <p>매핑 컬럼</p>
 * <ul>
 *     <li>{@code model_key}: PK, IDENTITY</li>
 *     <li>{@code model_name}: 모델 원장 이름(UNIQUE)</li>
 *     <li>{@code comm_interface}: HSMS/SOCKET</li>
 *     <li>{@code maker}: 제조사/공급사 식별 문자열(선택)</li>
 *     <li>{@code created_by}, {@code updated_by}: 감사용 사용자 식별자</li>
 * </ul>
 */
@Entity
@Table(
        name = "tc_model",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tc_model_model_name", columnNames = {"model_name"})
        }
)
public class TcModelEntity extends AbstractCreatedUpdatedEntity {

    /**
     * tc_model PK입니다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "model_key", nullable = false)
    private Long modelKey;

    /**
     * 모델 원장 이름입니다.
     */
    @Column(name = "model_name", length = 128, nullable = false)
    private String modelName;

    /**
     * 통신 인터페이스 유형입니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "comm_interface", length = 16, nullable = false)
    private ProtocolType commInterface;

    /**
     * 제조사/공급사 문자열입니다.
     */
    @Column(name = "maker", length = 32)
    private String maker;

    /**
     * 생성 사용자입니다.
     */
    @Column(name = "created_by", length = 50, nullable = false)
    private String createdBy;

    /**
     * 최종 수정 사용자입니다.
     */
    @Column(name = "updated_by", length = 50, nullable = false)
    private String updatedBy;

    /**
     * JPA 기본 생성자입니다.
     */
    public TcModelEntity() {
    }

    /**
     * 전체 필드 생성자입니다.
     *
     * @param modelKey tc_model PK
     * @param modelName 모델 원장 이름
     * @param commInterface 통신 인터페이스
     * @param maker 제조사/공급사
     * @param createdBy 생성 사용자
     * @param updatedBy 최종 수정 사용자
     */
    public TcModelEntity(
            Long modelKey,
            String modelName,
            ProtocolType commInterface,
            String maker,
            String createdBy,
            String updatedBy
    ) {
        this.modelKey = modelKey;
        this.modelName = modelName;
        this.commInterface = commInterface;
        this.maker = maker;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    /**
     * 신규 원장 엔티티를 생성합니다.
     *
     * @param modelName 모델 이름
     * @return 생성된 엔티티
     */
    public static TcModelEntity newEntity(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be null/blank");
        }
        TcModelEntity entity = new TcModelEntity();
        entity.setModelName(modelName);
        return entity;
    }

    /**
     * INSERT 직전에 감사 컬럼 기본값을 보정합니다.
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
     * UPDATE 직전에 감사 컬럼 기본값을 보정합니다.
     */
    @PreUpdate
    private void applyUpdateDefaults() {
        if (this.updatedBy == null || this.updatedBy.isBlank()) {
            this.updatedBy = "SYSTEM";
        }
    }

    /**
     * tc_model PK를 반환합니다.
     */
    public Long getModelKey() {
        return modelKey;
    }

    /**
     * tc_model PK를 설정합니다.
     */
    public void setModelKey(Long modelKey) {
        this.modelKey = modelKey;
    }

    /**
     * 모델 이름을 반환합니다.
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * 모델 이름을 설정합니다.
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    /**
     * 통신 인터페이스를 반환합니다.
     */
    public ProtocolType getCommInterface() {
        return commInterface;
    }

    /**
     * 통신 인터페이스를 설정합니다.
     */
    public void setCommInterface(ProtocolType commInterface) {
        this.commInterface = commInterface;
    }

    /**
     * 제조사/공급사 정보를 반환합니다.
     */
    public String getMaker() {
        return maker;
    }

    /**
     * 제조사/공급사 정보를 설정합니다.
     */
    public void setMaker(String maker) {
        this.maker = maker;
    }

    /**
     * 생성 사용자를 반환합니다.
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * 생성 사용자를 설정합니다.
     */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * 최종 수정 사용자를 반환합니다.
     */
    public String getUpdatedBy() {
        return updatedBy;
    }

    /**
     * 최종 수정 사용자를 설정합니다.
     */
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
