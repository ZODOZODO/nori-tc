package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

/**
 * tc_eqp_socket 테이블 1행에 대응하는 순수 DTO입니다.
 *
 * <p>역할:</p>
 * <p>- SOCKET 전용 상세 설정(tc_eqp 공통 컬럼 제외)을 전달하는 읽기 전용 모델입니다.</p>
 * <p>- 소켓 프레이밍/문자셋/타임아웃/heartbeat/keepalive 설정을 해석할 때 사용합니다.</p>
 *
 * <p>PK/FK:</p>
 * <p>- eqp_key (tc_eqp.eqp_key) ON DELETE CASCADE</p>
 *
 * <p>주의:</p>
 * <p>- 연결 모드(ACTIVE/PASSIVE)는 더 이상 이 테이블에서 관리하지 않고 {@code tc_eqp.comm_mode}에서 공통 관리합니다.</p>
 * <p>- {@code socket_protocol_type}, {@code charset}는 DB 문자열 제약 기반이므로 도메인에서는 String으로 유지합니다.</p>
 */
public record TcEqpSocket(
        long eqpKey,
        String socketProtocolType,
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
