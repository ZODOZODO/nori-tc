package com.nori.tc.db.jpa.common.entity.eqp;

import com.nori.tc.db.jpa.common.entity.base.AbstractCreatedUpdatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * tc_eqp_param_version 테이블 매핑 엔티티입니다.
 *
 * <p>버전 설명은 파라미터 개별 설명이 아니라 설비 버전 메타데이터이므로
 * tc_eqp_param과 분리해 관리합니다.</p>
 */
@Entity
@Table(
        name = "tc_eqp_param_version",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tc_eqp_param_version_eqp_key_param_version",
                        columnNames = {"eqp_key", "param_version"}
                )
        }
)
public class TcEqpParamVersionEntity extends AbstractCreatedUpdatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "eqp_param_version_key", nullable = false)
    private Long eqpParamVersionKey;

    @Column(name = "eqp_key", nullable = false)
    private Long eqpKey;

    @Column(name = "param_version", length = 100, nullable = false)
    private String paramVersion;

    @Column(name = "version_description", length = 2000)
    private String versionDescription;

    @Column(name = "created_by", length = 50, nullable = false)
    private String createdBy;

    @Column(name = "updated_by", length = 50, nullable = false)
    private String updatedBy;

    public TcEqpParamVersionEntity() {
    }

    public TcEqpParamVersionEntity(
            final Long eqpParamVersionKey,
            final Long eqpKey,
            final String paramVersion,
            final String versionDescription,
            final String createdBy,
            final String updatedBy
    ) {
        this.eqpParamVersionKey = eqpParamVersionKey;
        this.eqpKey = eqpKey;
        this.paramVersion = paramVersion;
        this.versionDescription = versionDescription;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    /**
     * Unique key 기준 신규 엔티티를 생성합니다.
     */
    public static TcEqpParamVersionEntity newEntity(final long eqpKey, final String paramVersion) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        if (paramVersion == null || paramVersion.isBlank()) {
            throw new IllegalArgumentException("paramVersion must not be null/blank");
        }

        final TcEqpParamVersionEntity entity = new TcEqpParamVersionEntity();
        entity.setEqpKey(eqpKey);
        entity.setParamVersion(paramVersion);
        return entity;
    }

    /**
     * 생성 시 감사 컬럼 기본값을 보정합니다.
     */
    @PrePersist
    private void applyCreateDefaults() {
        super.onCreate();
        if (this.createdBy == null || this.createdBy.isBlank()) {
            this.createdBy = "SYSTEM";
        }
        if (this.updatedBy == null || this.updatedBy.isBlank()) {
            this.updatedBy = "SYSTEM";
        }
    }

    /**
     * 수정 시 감사 사용자 기본값을 보정합니다.
     */
    @PreUpdate
    private void applyUpdateDefaults() {
        super.onUpdate();
        if (this.updatedBy == null || this.updatedBy.isBlank()) {
            this.updatedBy = "SYSTEM";
        }
    }

    public Long getEqpParamVersionKey() {
        return eqpParamVersionKey;
    }

    public void setEqpParamVersionKey(final Long eqpParamVersionKey) {
        this.eqpParamVersionKey = eqpParamVersionKey;
    }

    public Long getEqpKey() {
        return eqpKey;
    }

    public void setEqpKey(final Long eqpKey) {
        this.eqpKey = eqpKey;
    }

    public String getParamVersion() {
        return paramVersion;
    }

    public void setParamVersion(final String paramVersion) {
        this.paramVersion = paramVersion;
    }

    public String getVersionDescription() {
        return versionDescription;
    }

    public void setVersionDescription(final String versionDescription) {
        this.versionDescription = versionDescription;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(final String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(final String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
