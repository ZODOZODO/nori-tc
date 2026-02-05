package com.nori.tc.db.domain.eqp;

/**
 * tc_eqp_socket_protocol_type 테이블 1행에 대응하는 순수 DTO.
 *
 * 용도:
 * - 장비 소켓 통신에서 사용하는 프로토콜 타입과 파싱 규칙(룰/정규식)을 정의합니다.
 * - tc_eqp_socket.socket_protocol_type 컬럼에서 FK로 참조됩니다.
 *
 * 컬럼 설명:
 * - socketProtocolType      : PK (varchar(32))
 * - socketProtocolTypeName  : 사람이 읽는 이름 (varchar(100))
 * - parseStartRule          : 전문 파싱 시작 규칙 (varchar(1000), nullable)
 * - parseEndRule            : 전문 파싱 종료 규칙 (varchar(1000), nullable)
 * - parseRegex              : 전문 파싱 정규식 (varchar(1000), nullable)
 * - description             : 비고/설명 (varchar(1000), nullable)
 */
public record TcEqpSocketProtocolType(
        String socketProtocolType,
        String socketProtocolTypeName,
        String parseStartRule,
        String parseEndRule,
        String parseRegex,
        String description
) {
}
