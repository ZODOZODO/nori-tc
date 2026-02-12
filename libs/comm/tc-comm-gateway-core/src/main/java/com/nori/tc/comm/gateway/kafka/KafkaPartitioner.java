package com.nori.tc.comm.gateway.kafka;

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

    
    /**
     * 게이트웨이 Kafka 어댑터 구성 요소를 초기화합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     */
    private KafkaPartitioner() {
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @param partitionCount 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
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
