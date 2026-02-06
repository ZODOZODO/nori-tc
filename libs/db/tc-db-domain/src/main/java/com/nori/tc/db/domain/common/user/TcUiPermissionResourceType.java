package com.nori.tc.db.domain.common.user;

/**
 * UI 권한의 리소스 타입 (tc_ui_permission.resource_type).
 *
 * <p>
 * 체크 제약 조건:
 * - PAGE: 화면/라우팅/메뉴 등 UI 페이지 단위 리소스
 * - API : 백엔드 API 엔드포인트 리소스
 * </p>
 *
 * <p>
 * 주의:
 * - DB 저장 값과 enum 이름이 1:1로 매핑된다.
 * - MyBatis에서는 EnumTypeHandler를 사용하여 문자열로 저장한다.
 * </p>
 */
public enum TcUiPermissionResourceType {
    PAGE,
    API
}
