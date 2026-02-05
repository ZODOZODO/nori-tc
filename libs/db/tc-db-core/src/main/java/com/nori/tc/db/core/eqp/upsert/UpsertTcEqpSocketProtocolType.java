package com.nori.tc.db.core.eqp.upsert;

/**
 * tc_eqp_socket_protocol_type upsert 입력(Command)
 *
 * 설계 포인트:
 * - socketProtocolType/socketProtocolTypeName는 NOT NULL이므로 입력 필수입니다.
 * - parse* / description은 nullable 컬럼이므로 null 허용합니다.
 */
public record UpsertTcEqpSocketProtocolType(
        String socketProtocolType,
        String socketProtocolTypeName,
        String parseStartRule,
        String parseEndRule,
        String parseRegex,
        String description
) {
}
