package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

/**
 * tc_eqp_hsms 테이블 1행에 대응하는 순수 DTO입니다.
 *
 * <p>역할:</p>
 * <p>- HSMS 전용 상세 설정(tc_eqp 공통 컬럼 제외)을 전달하는 읽기 전용 모델입니다.</p>
 * <p>- Gateway/Business가 HSMS 프로토콜 타이머/링크테스트/메시지 크기 제한 설정을 해석할 때 사용합니다.</p>
 *
 * <p>[DB 스키마 요약]</p>
 * <p>- eqp_key            : bigint PK/FK (tc_eqp.eqp_key, ON DELETE CASCADE)</p>
 * <p>- device_id          : int (0~32767)</p>
 * <p>- t3_timeout         : int (default 45)</p>
 * <p>- t5_timeout         : int (default 10)</p>
 * <p>- t6_timeout         : int (default 5)</p>
 * <p>- t7_timeout         : int (default 10)</p>
 * <p>- t8_timeout         : int (default 5)</p>
 * <p>- link_test_enabled  : boolean (default true)</p>
 * <p>- link_test_interval : int (default 60)</p>
 * <p>- max_msg_bytes      : bigint (default 10485760)</p>
 * <p>- created_at         : timestamptz</p>
 * <p>- updated_at         : timestamptz</p>
 *
 * <p>주의:</p>
 * <p>- 연결 모드(ACTIVE/PASSIVE)는 더 이상 이 테이블에서 관리하지 않고 {@code tc_eqp.comm_mode}에서 공통 관리합니다.</p>
 * <p>- Gateway/Business는 하위 테이블의 mode를 읽지 않고, 반드시 {@code TcEqp.commMode()}를 사용해야 합니다.</p>
 * <p>- t3~t8, interval, max_msg_bytes 등은 DB에서 양수 제약이 있으므로 입력 검증은 상위 계층에서 추가해도 됩니다.</p>
 */
public record TcEqpHsms(
        long eqpKey,
        int deviceId,
        int t3Timeout,
        int t5Timeout,
        int t6Timeout,
        int t7Timeout,
        int t8Timeout,
        boolean linkTestEnabled,
        int linkTestInterval,
        long maxMsgBytes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
