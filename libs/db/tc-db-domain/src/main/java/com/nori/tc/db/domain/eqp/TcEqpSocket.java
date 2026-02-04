package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

/**
 * tc_eqp_socket 테이블 1행에 대응하는 순수 DTO.
 *
 * PK/FK:
 * - eqp_id (tc_eqp.eqp_id) ON DELETE CASCADE
 *
 * socket_protocol_type / charset는 현재 DB 제약이 없으므로 String으로 둡니다.
 */
public record TcEqpSocket(
        String eqpId,
        String socketProtocolType,
        String charset,
        boolean heartbeatEnabled,
        int heartbeatIntervalMs,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
