package com.nori.tc.comm.core.routing;

/**
 * 발행 모드
 *
 * - OUTBOX       : DB outbox에 먼저 적재(무유실 우선)
 * - DIRECT_KAFKA : Kafka로 즉시 발행(예외 allow-list로만 제한 권장)
 */
public enum PublishMode {
    OUTBOX,
    DIRECT_KAFKA
}
