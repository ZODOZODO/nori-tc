package com.nori.tc.db.core.user.upsert;

/**
 * tc_user_group upsert 입력(Command).
 *
 * <p>
 * 상세 규칙:
 * </p>
 * <ul>
 *     <li>groupId: 존재하면 PK 기반 갱신, 없으면 groupCode 기반 upsert</li>
 *     <li>groupCode: 필수/유니크(중복 시 갱신 대으로 간주)</li>
 *     <li>groupName: 필수(표시용 이름)</li>
 *     <li>description: 선택(최대 1000자)</li>
 *     <li>isActive: 필수(활성/비활성 여부)</li>
 * </ul>
 *
 * <p>
 * 주의:
 * - created_at/updated_at은 DB 또는 구현체에서 관리합니다.
 * </p>
 */
public record UpsertTcUserGroup(
        Long groupId,
        String groupCode,
        String groupName,
        String description,
        boolean isActive
) {
}
