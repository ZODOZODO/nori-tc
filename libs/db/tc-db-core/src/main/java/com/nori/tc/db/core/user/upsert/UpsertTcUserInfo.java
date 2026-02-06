package com.nori.tc.db.core.user.upsert;

import com.nori.tc.db.domain.common.user.UserStatus;

/**
 * tc_user_info upsert 입력(Command)
 *
 * 설계 포인트
 * - user_pk는 선택(Optional)이며, 있으면 PK 기반 갱신을 우선한다.
 * - user_id_norm과 email은 유니크 키이므로 중복을 조심해야 한다.
 * - status가 null이면 ACTIVE로 간주하는 것을 권장한다.
 * - created_by/updated_by가 null이면 DB default(SYSTEM)에 위임한다.
 */
public record UpsertTcUserInfo(
        Long userPk,
        String company,
        String department,
        String userName,
        String userId,
        String userIdNorm,
        String passwordHash,
        String email,
        UserStatus status,
        String createdBy,
        String updatedBy
) {
}
