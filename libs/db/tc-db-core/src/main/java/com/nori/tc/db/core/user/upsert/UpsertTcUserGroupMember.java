package com.nori.tc.db.core.user.upsert;

import java.time.OffsetDateTime;

/**
 * tc_user_group_member upsert 입력(Command).
 *
 * <p>
 * - ugmKey가 있으면 해당 PK 기준으로 갱신을 시도한다.
 * - ugmKey가 없으면 (user_pk, group_id) 유니크 키를 기준으로
 *   존재 여부를 확인한 뒤 갱신/생성을 수행한다.
 * </p>
 *
 * <p>
 * 주의:
 * - granted_at은 기본적으로 DB 기본값(CURRENT_TIMESTAMP)을 사용하도록 두되,
 *   필요 시 명시적으로 값을 지정할 수 있다.
 * </p>
 */
public record UpsertTcUserGroupMember(
        Long ugmKey,
        long userPk,
        long groupId,
        OffsetDateTime grantedAt,
        String grantedBy
) {
}
