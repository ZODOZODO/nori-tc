package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

/**
 * tc_eqp_hsms 테이블 1행에 대응하는 순수 DTO.
 *
 * PK/FK:
 * - eqp_id (tc_eqp.eqp_id) ON DELETE CASCADE
 *
 * 주의:
 * - t3~t8, interval, max_msg_bytes 등은 DB에서 양수 제약이 있으므로
 *   입력 검증은 상위 계층(서비스/포트 어댑터)에서 추가로 해도 됩니다.
 */
public record TcEqpHsms(
        String eqpId,
        int deviceId,
        int t3Ms,
        int t5Ms,
        int t6Ms,
        int t7Ms,
        int t8Ms,
        boolean linktestEnabled,
        int linktestIntervalMs,
        int maxMsgBytes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
