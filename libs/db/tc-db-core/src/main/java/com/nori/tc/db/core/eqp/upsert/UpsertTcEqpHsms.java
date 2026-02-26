package com.nori.tc.db.core.eqp.upsert;

import java.time.OffsetDateTime;

/**
 * tc_eqp_hsms upsert 입력(Command)입니다.
 *
 * <p>역할:</p>
 * <p>- HSMS 전용 상세 설정을 {@code tc_eqp_hsms} 테이블에 upsert하기 위한 입력 모델입니다.</p>
 *
 * <p>입력 규칙:</p>
 * <p>- {@code eqpKey}는 {@code tc_eqp.eqp_key}를 참조하는 PK/FK입니다.</p>
 * <p>- 연결 모드(ACTIVE/PASSIVE)는 이 입력 모델에서 더 이상 받지 않습니다.</p>
 * <p>- 연결 모드는 {@code tc_eqp.comm_mode}를 통해 별도로 저장/관리합니다.</p>
 * <p>- created_at/updated_at은 DB(또는 구현체)가 관리하는 것을 권장합니다.</p>
 *
 * <p>변경 배경:</p>
 * <p>- 중복 컬럼 제거를 위해 {@code tc_eqp_hsms.connection_mode}를 제거하고 {@code tc_eqp.comm_mode}로 통합했습니다.</p>
 */
public record UpsertTcEqpHsms(
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
