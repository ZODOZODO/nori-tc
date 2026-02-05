package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

/**
 * tc_eqp_socket 테이블 1행에 대응하는 순수 DTO.
 *
 * PK/FK:
 * - eqp_key (tc_eqp.eqp_key) ON DELETE CASCADE
 *
 * socket_protocol_type / connection_mode / charset는 DB에서 문자열 제약만 있으므로 String으로 둡니다.
 * - connection_mode는 CHECK 제약이 존재하므로 (ACTIVE|PASSIVE)만 허용됨
 */
public record TcEqpSocket(
        long eqpKey,
        String socketProtocolType,
        String connectionMode,
        String charset,
        boolean heartbeatEnabled,
        int heartbeatInterval,
        int readTimeout,
        int writeTimeout,
        int maxFrameSizeBytes,
        boolean keepAliveEnabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
