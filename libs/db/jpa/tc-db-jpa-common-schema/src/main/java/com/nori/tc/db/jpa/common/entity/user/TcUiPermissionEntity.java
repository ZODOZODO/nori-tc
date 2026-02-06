package com.nori.tc.db.jpa.common.entity.user;

import com.nori.tc.db.domain.common.user.TcUiPermissionMatchType;
import com.nori.tc.db.domain.common.user.TcUiPermissionResourceType;
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
 * tc_ui_permission 테이블 매핑 엔티티.
 *
 * [DB 스키마]
 * - perm_id       : bigint PK (IDENTITY)
 * - perm_code     : varchar(80) NOT NULL (UK)
 * - perm_name     : varchar(120) NOT NULL
 * - resource_type : varchar(10) NOT NULL (PAGE/API)
 * - match_type    : varchar(10) NOT NULL default 'PREFIX'
 * - resource      : varchar(255) NOT NULL
 * - http_method   : varchar(10) NULL
 * - description   : varchar(1000) NULL
 * - is_active     : boolean NOT NULL default true
 * - created_at    : timestamptz NOT NULL
 * - updated_at    : timestamptz NOT NULL
 * - created_by    : varchar(50) NOT NULL default 'SYSTEM'
 * - updated_by    : varchar(50) NOT NULL default 'SYSTEM'
 * - Constraints   : UNIQUE (perm_code)
 *
 * [설계 포인트]
 * 1. MapStruct 호환성:
 * - public 기본 생성자/전체 인자 생성자를 제공합니다.
 *
 * 2. 기본값 방어:
 * - match_type이 null이면 PREFIX로 보정합니다.
 * - is_active가 null이면 true로 보정합니다.
 * - created_by/updated_by가 비어있으면 SYSTEM으로 보정합니다.
 */
@Entity
@Table(
        name = "tc_ui_permission",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tc_ui_permission_perm_code", columnNames = {"perm_code"})
        }
)
public class TcUiPermissionEntity extends AbstractCreatedUpdatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "perm_id", nullable = false)
    private Long permId;

    @Column(name = "perm_code", length = 80, nullable = false)
    private String permCode;

    @Column(name = "perm_name", length = 120, nullable = false)
    private String permName;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 10, nullable = false)
    private TcUiPermissionResourceType resourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", length = 10, nullable = false)
    private TcUiPermissionMatchType matchType;

    @Column(name = "resource", length = 255, nullable = false)
    private String resource;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

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
    public TcUiPermissionEntity() {
    }

    /**
     * 전체 인자 생성자
     */
    public TcUiPermissionEntity(
            Long permId,
            String permCode,
            String permName,
            TcUiPermissionResourceType resourceType,
            TcUiPermissionMatchType matchType,
            String resource,
            String httpMethod,
            String description,
            Boolean isActive,
            String createdBy,
            String updatedBy
    ) {
        this.permId = permId;
        this.permCode = permCode;
        this.permName = permName;
        this.resourceType = resourceType;
        this.matchType = matchType;
        this.resource = resource;
        this.httpMethod = httpMethod;
        this.description = description;
        this.isActive = isActive;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    // =========================================================================
    // Static Factory
    // =========================================================================

    public static TcUiPermissionEntity newEntity(String permCode) {
        if (permCode == null || permCode.isBlank()) {
            throw new IllegalArgumentException("permCode must not be null/blank");
        }
        TcUiPermissionEntity e = new TcUiPermissionEntity();
        e.setPermCode(permCode);
        return e;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * DB Insert 전 데이터 보정
     * - match_type이 비어있으면 PREFIX로 보정
     * - is_active가 비어있으면 true로 보정
     * - created_by/updated_by가 비어있으면 SYSTEM으로 보정
     */
    @PrePersist
    private void applyDefaults() {
        if (this.matchType == null) {
            this.matchType = TcUiPermissionMatchType.PREFIX;
        }
        if (this.isActive == null) {
            this.isActive = Boolean.TRUE;
        }
        if (this.createdBy == null || this.createdBy.isBlank()) {
            this.createdBy = "SYSTEM";
        }
        if (this.updatedBy == null || this.updatedBy.isBlank()) {
            this.updatedBy = "SYSTEM";
        }
    }

    /**
     * DB Update 전 데이터 보정
     * - match_type이 비어있으면 PREFIX로 보정
     * - is_active가 비어있으면 true로 보정
     * - updated_by가 비어있으면 SYSTEM으로 보정
     */
    @PreUpdate
    private void applyUpdateDefaults() {
        if (this.matchType == null) {
            this.matchType = TcUiPermissionMatchType.PREFIX;
        }
        if (this.isActive == null) {
            this.isActive = Boolean.TRUE;
        }
        if (this.updatedBy == null || this.updatedBy.isBlank()) {
            this.updatedBy = "SYSTEM";
        }
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    public Long getPermId() {
        return permId;
    }

    public void setPermId(Long permId) {
        this.permId = permId;
    }

    public String getPermCode() {
        return permCode;
    }

    public void setPermCode(String permCode) {
        this.permCode = permCode;
    }

    public String getPermName() {
        return permName;
    }

    public void setPermName(String permName) {
        this.permName = permName;
    }

    public TcUiPermissionResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(TcUiPermissionResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public TcUiPermissionMatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(TcUiPermissionMatchType matchType) {
        this.matchType = matchType;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
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
