package com.nori.tc.db.jpa.common.entity.user;

import com.nori.tc.db.domain.common.user.UserStatus;
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
 * tc_user_info 테이블 매핑 엔티티.
 *
 * [DB 스키마]
 * - user_pk       : bigint PK (IDENTITY)
 * - company       : varchar(100) NOT NULL
 * - department    : varchar(100) NOT NULL
 * - user_name     : varchar(100) NOT NULL
 * - user_id       : varchar(50) NOT NULL
 * - user_id_norm  : varchar(50) NOT NULL UNIQUE
 * - password_hash : varchar(255) NOT NULL
 * - email         : varchar(255) NOT NULL UNIQUE
 * - status        : varchar(20) NOT NULL default 'ACTIVE'
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
 * - @PrePersist/@PreUpdate를 통해 insert/update 전 null 방어 로직을 수행합니다.
 */
@Entity
@Table(name = "tc_user_info")
public class TcUserInfoEntity extends AbstractCreatedUpdatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_pk", nullable = false)
    private Long userPk;

    @Column(name = "company", length = 100, nullable = false)
    private String company;

    @Column(name = "department", length = 100, nullable = false)
    private String department;

    @Column(name = "user_name", length = 100, nullable = false)
    private String userName;

    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    @Column(name = "user_id_norm", length = 50, nullable = false, unique = true)
    private String userIdNorm;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "email", length = 255, nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private UserStatus status;

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
    public TcUserInfoEntity() {
    }

    /**
     * 전체 인자 생성자
     * - MapStruct가 Domain -> Entity 변환 시 모든 필드를 한 번에 주입할 때 사용
     * - Store에서 수동으로 객체를 생성해야 할 때 사용
     */
    public TcUserInfoEntity(Long userPk, String company, String department, String userName, String userId, String userIdNorm,
                            String passwordHash, String email, UserStatus status, String createdBy, String updatedBy) {
        this.userPk = userPk;
        this.company = company;
        this.department = department;
        this.userName = userName;
        this.userId = userId;
        this.userIdNorm = userIdNorm;
        this.passwordHash = passwordHash;
        this.email = email;
        this.status = status;
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
    public static TcUserInfoEntity newEntity(String userIdNorm) {
        if (userIdNorm == null || userIdNorm.isBlank()) {
            throw new IllegalArgumentException("userIdNorm must not be null/blank");
        }
        TcUserInfoEntity e = new TcUserInfoEntity();
        e.setUserIdNorm(userIdNorm);
        return e;
    }

    /**
     * DB Insert 전 데이터 보정
     * - status가 null이면 ACTIVE로 강제 설정 (DB Default 준수)
     */
    @PrePersist
    private void applyDefaults() {
        if (this.status == null) {
            this.status = UserStatus.ACTIVE;
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
        if (this.status == null) {
            this.status = UserStatus.ACTIVE;
        }
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    public Long getUserPk() {
        return userPk;
    }

    public void setUserPk(Long userPk) {
        this.userPk = userPk;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserIdNorm() {
        return userIdNorm;
    }

    public void setUserIdNorm(String userIdNorm) {
        this.userIdNorm = userIdNorm;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
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
