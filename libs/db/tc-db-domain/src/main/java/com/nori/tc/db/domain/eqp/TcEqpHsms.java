package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

/**
 * tc_eqp_hsms 테이블 1행에 대응하는 순수 DTO.
 *
 * [DB 스키마 요약]
 * - eqp_key            : bigint PK/FK (tc_eqp.eqp_key, ON DELETE CASCADE)
 * - device_id          : int (0~32767)
 * - connection_mode    : varchar(10) (ACTIVE, PASSIVE)
 * - t3_timeout         : int (default 45)
 * - t5_timeout         : int (default 10)
 * - t6_timeout         : int (default 5)
 * - t7_timeout         : int (default 10)
 * - t8_timeout         : int (default 5)
 * - link_test_enabled  : boolean (default true)
 * - link_test_interval : int (default 60)
 * - max_msg_bytes      : bigint (default 10485760)
 * - created_at         : timestamptz
 * - updated_at         : timestamptz
 *
 * 주의:
 * - t3~t8, interval, max_msg_bytes 등은 DB에서 양수 제약이 있으므로
 *   입력 검증은 상위 계층(서비스/포트 어댑터)에서 추가로 해도 됩니다.
 */
public record TcEqpHsms(
        long eqpKey,
        int deviceId,
        String connectionMode,
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
