package com.nori.tc.db.domain.user;

import java.time.OffsetDateTime;

/**
 * tc_user_group 테이블 1행에 대응하는 순수 DTO.
 *
 * <p>
 * 이 객체는 기술 의존성이 없는 도메인 레벨 표현이며, 아래 규칙을 전제로 합니다.
 * </p>
 * <ul>
 *     <li>group_id: DB에서 IDENTITY로 생성되는 PK</li>
 *     <li>group_code: 유니크 키(비즈니스 식별자)</li>
 *     <li>group_name: 화면/정책에서 표시되는 그룹명</li>
 *     <li>is_active: 활성 여부(true/false)</li>
 *     <li>created_at/updated_at: DB 혹은 구현체에서 자동 갱신</li>
 * </ul>
 *
 * @param groupId DB 식별자(PK)
 * @param groupCode 그룹 코드(유니크)
 * @param groupName 그룹 이름
 * @param description 그룹 설명(선택)
 * @param isActive 활성 여부(true/false)
 * @param createdAt 생성 시각(UTC 기준 저장/조회)
 * @param updatedAt 최종 수정 시각(UTC 기준 저장/조회)
 */
public record TcUserGroup(
        long groupId,
        String groupCode,
        String groupName,
        String description,
        boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
