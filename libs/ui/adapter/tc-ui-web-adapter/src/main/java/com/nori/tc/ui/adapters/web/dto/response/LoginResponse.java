package com.nori.tc.ui.adapters.web.dto.response;

import java.time.OffsetDateTime;

/**
 * POST /auth/login 성공 응답 DTO입니다.
 *
 * <p>클라이언트는 반환된 {@code token}을 이후 모든 API 요청의
 * {@code Authorization: Bearer {token}} 헤더에 포함해야 합니다.</p>
 *
 * @param token     발급된 세션 토큰 (64자 alphanumeric)
 * @param userPk    로그인한 사용자 PK
 * @param issuedAt  토큰 발급 시각
 * @param expiresAt 토큰 만료 시각
 */
public record LoginResponse(
        String token,
        long userPk,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt
) {
}
