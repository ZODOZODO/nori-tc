package com.nori.tc.db.domain.user;

import java.time.OffsetDateTime;

/**
 * tc_user_group_permission 테이블 1행에 대응하는 순수 DTO.
 *
 * <pre>
 * [DB 스키마 요약]
 * - ugp_key    : bigint PK (IDENTITY)
 * - group_id   : bigint NOT NULL (FK -> tc_user_group.group_id)
 * - perm_id    : bigint NOT NULL (FK -> tc_ui_permission.perm_id)
 * - granted_at : timestamptz NOT NULL (default CURRENT_TIMESTAMP)
 * - granted_by : varchar(50) NULL
 *
 * [제약]
 * - PK: pk_tc_user_group_permission (ugp_key)
 * - UK: uk_tc_user_group_permission_group_id_perm_id (group_id, perm_id)
 * - FK: fk_tc_user_group_permission_group_id__tc_user_group (ON DELETE CASCADE)
 * - FK: fk_tc_user_group_permission_perm_id__tc_ui_permission (ON DELETE CASCADE)
 *
 * [설계 포인트]
 * - ugp_key는 DB에서 생성되는 IDENTITY 값입니다.
 * - granted_at은 DB의 CURRENT_TIMESTAMP 기본값을 사용하도록 설계합니다.
 * - granted_by는 NULL 가능 컬럼이므로 Java에서도 null 허용 타입으로 둡니다.
 * </pre>
 */
public record TcUserGroupPermission(
        long ugpKey,
        long groupId,
        long permId,
        OffsetDateTime grantedAt,
        String grantedBy
) {
}
