package com.nori.tc.db.domain.common;

/**
 * 프로토콜 타입 (tc_eqp.comm_interface, tc_model.protocol_type)
 *
 * DB Check Constraint:
 * - HSMS
 * - SOCKET
 *
 * 주의:
 * - 이 enum은 "도메인/데이터 표현" 전용입니다.
 * - JPA/MyBatis가 사용하는 값 매핑은 Adapter 계층에서 책임집니다.
 */
public enum ProtocolType {
    HSMS,
    SOCKET
}
