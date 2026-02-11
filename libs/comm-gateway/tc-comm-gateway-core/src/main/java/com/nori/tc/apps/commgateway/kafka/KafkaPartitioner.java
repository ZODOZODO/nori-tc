package com.nori.tc.apps.commgateway.kafka;

import org.apache.kafka.common.utils.Utils;

import java.nio.charset.StandardCharsets;

/**
 * Kafka partition calculator (murmur2 + toPositive).
 *
 * 주의
 * - 반드시 Kafka 기본 파티셔닝 로직과 동일해야 합니다.
 * - eqpId 파티션 계산 결과는 PASSIVE 바인딩 검증에도 사용됩니다.
 */
public final class KafkaPartitioner {

    private KafkaPartitioner() {
    }

    public static int partitionForKey(final String key, final int partitionCount) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
        if (partitionCount <= 0) {
            throw new IllegalArgumentException("partitionCount must be > 0");
        }

        final byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        return Utils.toPositive(Utils.murmur2(bytes)) % partitionCount;
    }
}
