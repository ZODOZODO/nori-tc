package com.nori.tc.messaging.domain.kafka;

import com.nori.tc.messaging.domain.kafka.contract.TcMesKafkaMetadata;
import com.nori.tc.messaging.domain.kafka.key.KafkaMessageKeyResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * KafkaMessageKeyResolver 단위 테스트입니다.
 *
 * <p>Phase 1에서 확정한 key 규칙(MES=correlationId, 그 외=eqpid)이
 * 코드에서 정확히 반영되는지 검증합니다.</p>
 */
class KafkaMessageKeyResolverTest {

    /**
     * 검증 대상 key 리졸버입니다.
     */
    private final KafkaMessageKeyResolver keyResolver = new KafkaMessageKeyResolver();

    /**
     * MES 토픽 key가 correlationId로 계산되는지 확인합니다.
     */
    @Test
    @DisplayName("MES 토픽은 correlationId를 key로 사용한다")
    void resolveMesTopicByCorrelationId() {
        final String resolved = keyResolver.resolve(TcKafkaTopics.MES_EVENTS, "EQP-01", "LOT-123");
        assertEquals("LOT-123", resolved);
    }

    /**
     * 비-MES 토픽 key가 eqpid로 계산되는지 확인합니다.
     */
    @Test
    @DisplayName("비-MES 토픽은 eqpid를 key로 사용한다")
    void resolveNonMesTopicByEqpid() {
        final String resolved = keyResolver.resolve(TcKafkaTopics.EQP_EVENTS, "EQP-01", "LOT-123");
        assertEquals("EQP-01", resolved);
    }

    /**
     * metadata 기반 리졸브 시 MES metadata에서 correlationId를 추출하는지 확인합니다.
     */
    @Test
    @DisplayName("MES metadata 기반 resolve는 correlationId를 사용한다")
    void resolveByMetadataForMesTopic() {
        final TcMesKafkaMetadata metadata = new TcMesKafkaMetadata(
                "MES_LOT_STARTED",
                "2026-02-19T09:00:00Z",
                TcKafkaSources.EXTERNAL_MES_ADAPTER,
                "LOT-777",
                "v1"
        );

        final String resolved = keyResolver.resolve(TcKafkaTopics.MES_COMMANDS, metadata, "EQP-XX");
        assertEquals("LOT-777", resolved);
    }

    /**
     * MES 토픽에서 correlationId가 없으면 예외가 발생해야 합니다.
     */
    @Test
    @DisplayName("MES 토픽에서 correlationId 누락 시 예외가 발생한다")
    void throwsWhenMesCorrelationIdIsMissing() {
        assertThrows(IllegalArgumentException.class,
                () -> keyResolver.resolve(TcKafkaTopics.MES_EVENTS, "EQP-01", "  "));
    }

    /**
     * 미지원 토픽은 즉시 예외 처리되는지 확인합니다.
     */
    @Test
    @DisplayName("미지원 토픽은 resolve할 수 없다")
    void throwsWhenTopicIsUnsupported() {
        assertThrows(IllegalArgumentException.class,
                () -> keyResolver.resolve("tc.unknown.topic", "EQP-01", "LOT-01"));
    }
}

