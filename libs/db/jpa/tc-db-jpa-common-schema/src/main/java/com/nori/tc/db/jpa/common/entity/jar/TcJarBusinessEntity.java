package com.nori.tc.db.jpa.common.entity.jar;

import com.nori.tc.db.jpa.common.entity.base.AbstractCreatedUpdatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * tc_jar_business 테이블 매핑 엔티티.
 *
 * [DB 스키마]
 * - eqp_key       : bigint PK/FK (tc_eqp.eqp_key, ON DELETE CASCADE)
 * - jar_file_name : varchar(255) NOT NULL
 * - jar_file      : bytea NOT NULL
 * - created_at    : timestamptz NOT NULL
 * - updated_at    : timestamptz NOT NULL
 * - created_by    : varchar(50) NOT NULL default 'SYSTEM'
 * - updated_by    : varchar(50) NOT NULL default 'SYSTEM'
 *
 * [설계 포인트]
 * - created_at/updated_at은 AbstractCreatedUpdatedEntity에서 공통 처리합니다.
 * - created_by/updated_by는 null/blank 방어를 위해 lifecycle 콜백에서 기본값을 보정합니다.
 * - jar_file은 bytea 컬럼이므로 @Lob 매핑을 명시합니다.
 */
@Entity
@Table(name = "tc_jar_business")
public class TcJarBusinessEntity extends AbstractCreatedUpdatedEntity {

    @Id
    @Column(name = "eqp_key", nullable = false)
    private Long eqpKey;

    @Column(name = "jar_file_name", length = 255, nullable = false)
    private String jarFileName;

    @Lob
    @Column(name = "jar_file", nullable = false)
    private byte[] jarFile;

    @Column(name = "created_by", length = 50, nullable = false)
    private String createdBy;

    @Column(name = "updated_by", length = 50, nullable = false)
    private String updatedBy;

    // =========================================================================
    // Constructors (MapStruct & JPA)
    // =========================================================================

    /**
     * 기본 생성자 (JPA/MapStruct 필수)
     */
    public TcJarBusinessEntity() {
    }

    /**
     * 전체 인자 생성자.
     */
    public TcJarBusinessEntity(
            Long eqpKey,
            String jarFileName,
            byte[] jarFile,
            String createdBy,
            String updatedBy
    ) {
        this.eqpKey = eqpKey;
        this.jarFileName = jarFileName;
        this.jarFile = jarFile;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    // =========================================================================
    // Static Factory
    // =========================================================================

    /**
     * 신규 엔티티 골격을 생성합니다.
     *
     * @param eqpKey 설비 키
     * @return 신규 엔티티
     */
    public static TcJarBusinessEntity newEntity(Long eqpKey) {
        if (eqpKey == null || eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be positive");
        }
        TcJarBusinessEntity entity = new TcJarBusinessEntity();
        entity.setEqpKey(eqpKey);
        return entity;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * INSERT 전 기본값 보정을 수행합니다.
     */
    @PrePersist
    private void applyCreateDefaults() {
        if (this.createdBy == null || this.createdBy.isBlank()) {
            this.createdBy = "SYSTEM";
        }
        if (this.updatedBy == null || this.updatedBy.isBlank()) {
            this.updatedBy = "SYSTEM";
        }
    }

    /**
     * UPDATE 전 기본값 보정을 수행합니다.
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

    public Long getEqpKey() {
        return eqpKey;
    }

    public void setEqpKey(Long eqpKey) {
        this.eqpKey = eqpKey;
    }

    public String getJarFileName() {
        return jarFileName;
    }

    public void setJarFileName(String jarFileName) {
        this.jarFileName = jarFileName;
    }

    public byte[] getJarFile() {
        return jarFile;
    }

    public void setJarFile(byte[] jarFile) {
        this.jarFile = jarFile;
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
