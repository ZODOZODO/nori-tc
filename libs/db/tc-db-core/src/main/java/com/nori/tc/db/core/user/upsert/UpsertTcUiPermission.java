package com.nori.tc.db.core.user.upsert;

import com.nori.tc.db.domain.common.user.TcUiPermissionMatchType;
import com.nori.tc.db.domain.common.user.TcUiPermissionResourceType;

/**
 * tc_ui_permission upsert 입력(Command).
 *
 * <p>
 * 설계 의도:
 * - perm_id가 있으면 PK 기반 갱신을 우선한다.
 * - perm_id가 없으면 perm_code UNIQUE 기준으로 생성/재조회한다.
 * </p>
 *
 * <p>
 * 컬럼 규칙:
 * - matchType이 null이면 DB 기본값(PREFIX)을 사용하도록 구현체가 보정한다.
 * - isActive는 not null이므로 true/false를 명시한다.
 * - createdAt/updatedAt은 DB 기본값 또는 구현체에서 관리한다.
 * - createdBy/updatedBy가 null이면 구현체에서 SYSTEM으로 보정 가능하다.
 * </p>
 */
public record UpsertTcUiPermission(
        Long permId,
        String permCode,
        String permName,
        TcUiPermissionResourceType resourceType,
        TcUiPermissionMatchType matchType,
        String resource,
        String httpMethod,
        String description,
        boolean isActive,
        String createdBy,
        String updatedBy
) {
}
