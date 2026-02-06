package com.nori.tc.db.domain.user;

import java.time.OffsetDateTime;

/**
 * tc_ui_auth_session 테이블 1행에 대응하는 순수 DTO.
 *
 * <p>
 * 사용 목적:
 * - UI 인증 세션의 생명주기(발급/만료/폐기)를 DB에 기록하고 조회하기 위한 레코드 모델입니다.
 * - MyBatis/JPA 등 영속 계층에서 그대로 사용하는 "불변" 데이터 구조입니다.
 * </p>
 *
 * <p>
 * PK:
 * - token : 세션 토큰(64자 내외), 절대 중복 불가
 * </p>
 *
 * <p>
 * FK:
 * - user_pk : tc_user_info.user_pk (사용자 식별자)
 * </p>
 *
 * <p>
 * 주요 컬럼 설명:
 * - issuedAt   : 세션 발급 시각 (DB 기본값 = CURRENT_TIMESTAMP)
 * - expiresAt  : 세션 만료 시각 (필수)
 * - lastSeenAt : 마지막 접근 시각 (옵션)
 * - revoked    : 강제 폐기 여부 (기본값=false)
 * </p>
 *
 * <p>
 * nullable 컬럼은 Java에서 null 허용 타입으로 둡니다.
 * </p>
 */
public record TcUiAuthSession(
        String token,
        long userPk,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime lastSeenAt,
        boolean revoked
) {
}
