package com.nori.tc.db.core.user.upsert;

import java.time.OffsetDateTime;

/**
 * tc_user_group_permission upsert 입력(Command).
 *
 * <p>
 * - (group_id, perm_id) 유니크 키를 기준으로 갱신/생성을 수행합니다.
 * - granted_at은 DB에서 CURRENT_TIMESTAMP로 갱신되도록 처리하는 것을 권장합니다.
 * - granted_by는 변경 가능 컬럼으로 간주합니다.
 * </p>
 */
public record UpsertTcUserGroupPermission(
        long groupId,
        long permId,
        OffsetDateTime grantedAt,
        String grantedBy
) {
}
