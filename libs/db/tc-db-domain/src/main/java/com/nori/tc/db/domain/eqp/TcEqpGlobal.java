package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

/**
 * tc_eqp_global 테이블 1행에 대응하는 순수 DTO.
 *
 * PK:
 * - eqp_global_key (IDENTITY, bigint)
 *
 * UK:
 * - (eqp_key, param_name)
 *
 * FK:
 * - eqp_key -> tc_eqp.eqp_key (ON DELETE CASCADE)
 *
 * 컬럼:
 * - param_name  : 전역 파라미터 이름 (최대 100자)
 * - param_value : 전역 파라미터 값 (text, nullable)
 * - updated_at  : 갱신 시간 (DB에서 CURRENT_TIMESTAMP 기본값)
 */
public record TcEqpGlobal(
        long eqpGlobalKey,
        long eqpKey,
        String paramName,
        String paramValue,
        OffsetDateTime updatedAt
) {
}
