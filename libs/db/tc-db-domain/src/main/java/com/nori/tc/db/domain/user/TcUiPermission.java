package com.nori.tc.db.domain.user;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.user.TcUiPermissionMatchType;
import com.nori.tc.db.domain.common.user.TcUiPermissionResourceType;

/**
 * tc_ui_permission 테이블 1행에 대응하는 순수 DTO.
 *
 * <p>
 * PK:
 * - perm_id (IDENTITY, bigint)
 *
 * UK:
 * - perm_code (권한 코드, 최대 80자)
 * </p>
 *
 * <p>
 * 주요 컬럼 의미:
 * - perm_code     : 외부 시스템/권한 정책과 연동되는 고유 코드
 * - perm_name     : 화면 표시용 권한 이름
 * - resource_type : PAGE/API 구분
 * - match_type    : EXACT/PREFIX/REGEX 매칭 방식
 * - resource      : 실제 리소스 문자열 (URL path, 라우팅 키 등)
 * - http_method   : API 권한일 때 HTTP 메서드 (nullable)
 * - description   : 권한 설명 (nullable)
 * - is_active     : 사용 여부 (true/false)
 * - created_at    : 생성 시각 (DB 기본값 CURRENT_TIMESTAMP)
 * - updated_at    : 갱신 시각 (DB 기본값 CURRENT_TIMESTAMP)
 * - created_by    : 생성자 (DB 기본값 SYSTEM)
 * - updated_by    : 수정자 (DB 기본값 SYSTEM)
 * </p>
 */
public record TcUiPermission(
        long permId,
        String permCode,
        String permName,
        TcUiPermissionResourceType resourceType,
        TcUiPermissionMatchType matchType,
        String resource,
        String httpMethod,
        String description,
        boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy
) {
}
