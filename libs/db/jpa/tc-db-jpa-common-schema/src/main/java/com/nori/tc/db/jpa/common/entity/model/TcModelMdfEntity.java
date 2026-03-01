package com.nori.tc.db.jpa.common.entity.model;

import com.nori.tc.db.jpa.common.entity.base.AbstractUpdatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * {@code tc_model_mdf} JPA 엔티티입니다.
 *
 * <p>
 * 핵심 제약:
 * 1) {@code model_version_key} 단일 UNIQUE
 * 2) {@code mdf_file}는 PostgreSQL 기준 {@code bytea} 컬럼
 * </p>
 */
@Entity
@Table(
        name = "tc_model_mdf",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tc_model_mdf_model_version_key", columnNames = {"model_version_key"})
        }
)
public class TcModelMdfEntity extends AbstractUpdatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mdf_key", nullable = false)
    private Long mdfKey;

    @Column(name = "model_version_key", nullable = false)
    private Long modelVersionKey;

    @Column(name = "mdf_name", length = 100, nullable = false)
    private String mdfName;

    /**
     * DB 스키마가 {@code bytea}이므로 OID(Lob)로 해석되지 않도록 명시합니다.
     */
    @Column(name = "mdf_file", nullable = false, columnDefinition = "bytea")
    private byte[] mdfFile;

    /**
     * JPA 기본 생성자입니다.
     */
    public TcModelMdfEntity() {
    }

    /**
     * 전체 필드 생성자입니다.
     *
     * @param mdfKey MDF PK
     * @param modelVersionKey 모델 버전 키
     * @param mdfName MDF 이름
     * @param mdfFile MDF 원본 바이너리
     */
    public TcModelMdfEntity(Long mdfKey, Long modelVersionKey, String mdfName, byte[] mdfFile) {
        this.mdfKey = mdfKey;
        this.modelVersionKey = modelVersionKey;
        this.mdfName = mdfName;
        this.mdfFile = mdfFile;
    }

    /**
     * 신규 엔티티를 생성합니다.
     *
     * @param modelVersionKey 모델 버전 키
     * @param mdfName MDF 이름
     * @param mdfFile MDF 원본 바이너리
     * @return 생성된 엔티티
     */
    public static TcModelMdfEntity newEntity(long modelVersionKey, String mdfName, byte[] mdfFile) {
        if (modelVersionKey <= 0) {
            throw new IllegalArgumentException("modelVersionKey must be > 0");
        }
        if (mdfName == null || mdfName.isBlank()) {
            throw new IllegalArgumentException("mdfName must not be null/blank");
        }
        if (mdfFile == null || mdfFile.length == 0) {
            throw new IllegalArgumentException("mdfFile must not be null/empty");
        }

        TcModelMdfEntity entity = new TcModelMdfEntity();
        entity.setModelVersionKey(modelVersionKey);
        entity.setMdfName(mdfName);
        entity.setMdfFile(mdfFile);
        return entity;
    }

    /**
     * MDF PK를 반환합니다.
     */
    public Long getMdfKey() {
        return mdfKey;
    }

    /**
     * MDF PK를 설정합니다.
     */
    public void setMdfKey(Long mdfKey) {
        this.mdfKey = mdfKey;
    }

    /**
     * 모델 버전 키를 반환합니다.
     */
    public Long getModelVersionKey() {
        return modelVersionKey;
    }

    /**
     * 모델 버전 키를 설정합니다.
     */
    public void setModelVersionKey(Long modelVersionKey) {
        this.modelVersionKey = modelVersionKey;
    }

    /**
     * MDF 이름을 반환합니다.
     */
    public String getMdfName() {
        return mdfName;
    }

    /**
     * MDF 이름을 설정합니다.
     */
    public void setMdfName(String mdfName) {
        this.mdfName = mdfName;
    }

    /**
     * MDF 바이너리를 반환합니다.
     */
    public byte[] getMdfFile() {
        return mdfFile;
    }

    /**
     * MDF 바이너리를 설정합니다.
     */
    public void setMdfFile(byte[] mdfFile) {
        this.mdfFile = mdfFile;
    }
}
