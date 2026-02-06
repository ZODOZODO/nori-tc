package com.nori.tc.db.jpa.common.entity.user;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * tc_ui_auth_session 테이블 매핑 엔티티.
 *
 * [DB 스키마]
 * - token        : varchar(64) NOT NULL (PK)
 * - user_pk      : bigint NOT NULL (FK -> tc_user_info.user_pk)
 * - issued_at    : timestamptz NOT NULL default CURRENT_TIMESTAMP
 * - expires_at   : timestamptz NOT NULL
 * - last_seen_at : timestamptz NULL
 * - revoked      : boolean NOT NULL default false
 *
 * [설계 포인트]
 * 1. 토큰 기반 PK:
 * - token은 외부에서 생성되는 식별자이므로 @GeneratedValue를 사용하지 않습니다.
 *
 * 2. 기본값 방어:
 * - issuedAt이 null이면 현재 시각으로 보정합니다.
 * - revoked가 null이면 false로 보정합니다.
 */
@Entity
@Table(name = "tc_ui_auth_session")
public class TcUiAuthSessionEntity {

    @Id
    @Column(name = "token", length = 64, nullable = false)
    private String token;

    @Column(name = "user_pk", nullable = false)
    private Long userPk;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "revoked", nullable = false)
    private Boolean revoked;

    // =========================================================================
    // Constructors (MapStruct & JPA)
    // =========================================================================

    /**
     * 기본 생성자 (필수)
     */
    public TcUiAuthSessionEntity() {
    }

    /**
     * 전체 인자 생성자
     */
    public TcUiAuthSessionEntity(
            String token,
            Long userPk,
            OffsetDateTime issuedAt,
            OffsetDateTime expiresAt,
            OffsetDateTime lastSeenAt,
            Boolean revoked
    ) {
        this.token = token;
        this.userPk = userPk;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.lastSeenAt = lastSeenAt;
        this.revoked = revoked;
    }

    // =========================================================================
    // Static Factory
    // =========================================================================

    /**
     * 신규 세션 엔티티 생성 (필수 키만 세팅)
     */
    public static TcUiAuthSessionEntity newEntity(String token, long userPk) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be null/blank");
        }
        if (token.length() > 64) {
            throw new IllegalArgumentException("token length must be <= 64");
        }
        if (userPk <= 0) {
            throw new IllegalArgumentException("userPk must be positive");
        }
        TcUiAuthSessionEntity e = new TcUiAuthSessionEntity();
        e.setToken(token);
        e.setUserPk(userPk);
        return e;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * DB Insert 전 기본값 방어
     * - issuedAt이 null이면 현재 시각으로 보정합니다.
     * - revoked가 null이면 false로 보정합니다.
     */
    @PrePersist
    private void applyDefaults() {
        if (this.issuedAt == null) {
            this.issuedAt = OffsetDateTime.now();
        }
        if (this.revoked == null) {
            this.revoked = Boolean.FALSE;
        }
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserPk() {
        return userPk;
    }

    public void setUserPk(Long userPk) {
        this.userPk = userPk;
    }

    public OffsetDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(OffsetDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(OffsetDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Boolean getRevoked() {
        return revoked;
    }

    public void setRevoked(Boolean revoked) {
        this.revoked = revoked;
    }
}
