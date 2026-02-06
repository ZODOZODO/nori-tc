package com.nori.tc.db.domain.common.user;

/**
 * 사용자 상태 (tc_user_info.status)
 *
 * DB Check Constraint:
 * - ACTIVE   : 정상 사용
 * - LOCKED   : 잠금(로그인 차단)
 * - DISABLED : 비활성(운영 정책에 의한 비활성)
 * - DELETED  : 논리 삭제(복구 가능)
 */
public enum UserStatus {
    ACTIVE,
    LOCKED,
    DISABLED,
    DELETED
}
