package com.nori.tc.db.core.user.upsert;

import java.time.OffsetDateTime;

/**
 * tc_ui_auth_session upsert 입력(Command).
 *
 * <p>
 * 상세 규칙:
 * - token     : 필수, 공백 불가, 길이 64 이내 권장 (DB PK)
 * - userPk    : 반드시 양수 (FK)
 * - issuedAt  : null이면 현재 시각으로 보정 (DB 기본값과 동일한 의미)
 * - expiresAt : 필수 (만료 시각)
 * - lastSeenAt: 선택 (최종 접속 시각)
 * - revoked   : null이면 false로 보정 (DB 기본값과 동일한 의미)
 * </p>
 */
public record UpsertTcUiAuthSession(
        String token,
        Long userPk,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime lastSeenAt,
        Boolean revoked
) {
}
