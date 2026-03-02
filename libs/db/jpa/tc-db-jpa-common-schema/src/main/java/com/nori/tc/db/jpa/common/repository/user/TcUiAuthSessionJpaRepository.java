package com.nori.tc.db.jpa.common.repository.user;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nori.tc.db.jpa.common.entity.user.TcUiAuthSessionEntity;

/**
 * tc_ui_auth_session Repository
 */
public interface TcUiAuthSessionJpaRepository extends JpaRepository<TcUiAuthSessionEntity, String> {

    /**
     * 유효한 세션을 조회합니다 (token 일치 + revoked=false + expiresAt > now).
     */
    Optional<TcUiAuthSessionEntity> findByTokenAndRevokedFalseAndExpiresAtAfter(
            String token,
            OffsetDateTime now
    );

    /**
     * 세션을 폐기합니다 (revoked = true).
     *
     * <p>SELECT 없이 UPDATE 단일 쿼리를 실행합니다.</p>
     */
    @Modifying
    @Query("UPDATE TcUiAuthSessionEntity s SET s.revoked = true WHERE s.token = :token")
    void revokeByToken(@Param("token") String token);

    /**
     * 마지막 접근 시각을 업데이트합니다.
     *
     * <p>SELECT 없이 UPDATE 단일 쿼리를 실행합니다.</p>
     */
    @Modifying
    @Query("UPDATE TcUiAuthSessionEntity s SET s.lastSeenAt = :lastSeenAt WHERE s.token = :token")
    void updateLastSeenAt(
            @Param("token") String token,
            @Param("lastSeenAt") OffsetDateTime lastSeenAt
    );
}
