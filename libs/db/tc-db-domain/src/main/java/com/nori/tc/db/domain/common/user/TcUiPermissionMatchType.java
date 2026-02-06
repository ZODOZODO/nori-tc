package com.nori.tc.db.domain.common.user;

/**
 * UI 권한의 리소스 매칭 방식 (tc_ui_permission.match_type).
 *
 * <p>
 * 체크 제약 조건:
 * - EXACT : 리소스 문자열이 완전히 일치할 때 허용
 * - PREFIX: 리소스 문자열의 접두사가 일치할 때 허용 (기본값)
 * - REGEX : 정규식 매칭 (운영 시 성능 주의)
 * </p>
 *
 * <p>
 * 주의:
 * - DB 저장 값과 enum 이름이 1:1로 매핑된다.
 * - MyBatis에서는 EnumTypeHandler를 사용하여 문자열로 저장한다.
 * </p>
 */
public enum TcUiPermissionMatchType {
    EXACT,
    PREFIX,
    REGEX
}
