package com.nori.tc.messaging.domain.kafka.key;

import com.nori.tc.messaging.domain.kafka.TcKafkaTopics;
import com.nori.tc.messaging.domain.kafka.contract.TcKafkaMetadata;
import com.nori.tc.messaging.domain.kafka.contract.TcMesKafkaMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 토픽별 Kafka key 결정 로직을 공통화한 유틸리티입니다.
 *
 * <p>규칙:
 * 1) MES 토픽(tc.mes.events, tc.mes.commands) -> correlationId 사용
 * 2) 나머지 토픽 -> eqpid 사용</p>
 */
public final class KafkaMessageKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageKeyResolver.class);

    /**
     * 기본 생성자입니다.
     */
    public KafkaMessageKeyResolver() {
        log.info("KafkaMessageKeyResolver initialized.");
    }

    /**
     * 토픽과 식별자 값을 기반으로 Kafka key를 계산합니다.
     *
     * @param topic 대상 토픽
     * @param eqpid 설비 식별자
     * @param correlationId MES 상관관계 식별자
     * @return 계산된 Kafka key
     */
    public String resolve(final String topic, final String eqpid, final String correlationId) {
        validateTopic(topic);
        if (TcKafkaTopics.isMesTopic(topic)) {
            final String normalizedCorrelationId = normalizeRequired(correlationId, "correlationId");
            if (log.isDebugEnabled()) {
                log.debug("Kafka key resolved by correlationId. topic={}, key={}", topic, normalizedCorrelationId);
            }
            return normalizedCorrelationId;
        }

        final String normalizedEqpId = normalizeRequired(eqpid, "eqpid");
        if (log.isDebugEnabled()) {
            log.debug("Kafka key resolved by eqpid. topic={}, key={}", topic, normalizedEqpId);
        }
        return normalizedEqpId;
    }

    /**
     * metadata 객체를 함께 받아 Kafka key를 계산합니다.
     *
     * <p>MES 토픽이면 metadata에서 correlationId를 추출하고,
     * 비-MES 토픽이면 전달된 eqpid를 사용합니다.</p>
     *
     * @param topic 대상 토픽
     * @param metadata 메시지 metadata
     * @param eqpid 설비 식별자
     * @return 계산된 Kafka key
     */
    public String resolve(final String topic, final TcKafkaMetadata metadata, final String eqpid) {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata is required");
        }

        if (TcKafkaTopics.isMesTopic(topic)) {
            if (!(metadata instanceof TcMesKafkaMetadata mesMetadata)) {
                throw new IllegalArgumentException("MES topic requires TcMesKafkaMetadata");
            }
            return resolve(topic, eqpid, mesMetadata.correlationId());
        }

        return resolve(topic, eqpid, null);
    }

    /**
     * 토픽 유효성을 검증합니다.
     *
     * @param topic 검증 대상 토픽
     */
    private static void validateTopic(final String topic) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic is required");
        }
        if (!TcKafkaTopics.isSupported(topic)) {
            throw new IllegalArgumentException("unsupported topic: " + topic);
        }
    }

    /**
     * 필수 문자열 값을 정규화하고 검증합니다.
     *
     * @param value 원본 값
     * @param fieldName 필드명
     * @return trim 적용된 값
     */
    private static String normalizeRequired(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}

