package com.nori.tc.db.domain.user;

import java.time.OffsetDateTime;

/**
 * tc_user_group_member 테이블 1행에 대응하는 순수 DTO.
 *
 * <p>
 * PK:
 * - ugm_key : bigint identity (PK)
 * </p>
 *
 * <p>
 * Unique:
 * - (user_pk, group_id)
 * </p>
 *
 * <p>
 * 주요 컬럼:
 * - user_pk    : 사용자 PK (필수)
 * - group_id   : 그룹 PK (필수)
 * - granted_at : 그룹 부여 시각 (DB 기본값 CURRENT_TIMESTAMP)
 * - granted_by : 부여자 식별자 (선택, 최대 50자)
 * </p>
 */
public record TcUserGroupMember(
        long ugmKey,
        long userPk,
        long groupId,
        OffsetDateTime grantedAt,
        String grantedBy
) {
}
