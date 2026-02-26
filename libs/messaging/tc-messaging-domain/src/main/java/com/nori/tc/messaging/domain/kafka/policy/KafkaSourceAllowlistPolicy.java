package com.nori.tc.messaging.domain.kafka.policy;

import com.nori.tc.messaging.domain.kafka.TcKafkaSources;
import com.nori.tc.messaging.domain.kafka.TcKafkaTopics;
import com.nori.tc.messaging.domain.kafka.validation.KafkaValidationDisposition;
import com.nori.tc.messaging.domain.kafka.validation.KafkaValidationErrorCode;
import com.nori.tc.messaging.domain.kafka.validation.KafkaValidationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 토픽별 source 허용 목록(allowlist) 정책입니다.
 *
 * <p>토픽 소유권 정책(A-01)을 런타임 검증 가능한 형태로 표현합니다.</p>
 */
public final class KafkaSourceAllowlistPolicy {

    private static final Logger log = LoggerFactory.getLogger(KafkaSourceAllowlistPolicy.class);

    /**
     * 토픽별 허용 source 집합입니다.
     */
    private final Map<String, Set<String>> allowlistByTopic;

    /**
     * 기본 소유권 매트릭스를 반영한 정책 객체를 생성합니다.
     *
     * @return 기본 정책 객체
     */
    public static KafkaSourceAllowlistPolicy defaultPolicy() {
        final Map<String, Set<String>> mapping = new LinkedHashMap<>();
        mapping.put(TcKafkaTopics.EQP_EVENTS, Set.of(TcKafkaSources.COMM_GATEWAY));
        mapping.put(TcKafkaTopics.EQP_COMMANDS, Set.of(TcKafkaSources.BUSINESS_CORE));
        mapping.put(TcKafkaTopics.MES_COMMANDS, Set.of(TcKafkaSources.BUSINESS_CORE));
        mapping.put(TcKafkaTopics.MES_EVENTS, Set.of(TcKafkaSources.EXTERNAL_MES_ADAPTER));
        // U1 설계 기준:
        // UI 이벤트는 Gateway 처리용 / Business 처리용 토픽으로 분리하되,
        // 발행 주체(source)는 모두 UI_BACKEND로 동일하게 관리합니다.
        mapping.put(TcKafkaTopics.UI_EVENTS_GATEWAY, Set.of(TcKafkaSources.UI_BACKEND));
        mapping.put(TcKafkaTopics.UI_EVENTS_BUSINESS, Set.of(TcKafkaSources.UI_BACKEND));
        mapping.put(TcKafkaTopics.UI_COMMANDS, Set.of(
                TcKafkaSources.COMM_GATEWAY,
                TcKafkaSources.BUSINESS_CORE
        ));
        if (log.isDebugEnabled()) {
            log.debug("KafkaSourceAllowlistPolicy default mapping prepared. uiGatewayTopic={}, uiBusinessTopic={}",
                    TcKafkaTopics.UI_EVENTS_GATEWAY,
                    TcKafkaTopics.UI_EVENTS_BUSINESS);
        }
        return new KafkaSourceAllowlistPolicy(mapping);
    }

    /**
     * 사용자 정의 allowlist로 정책 객체를 생성합니다.
     *
     * @param allowlistByTopic 토픽별 허용 source 매핑
     */
    public KafkaSourceAllowlistPolicy(final Map<String, Set<String>> allowlistByTopic) {
        Objects.requireNonNull(allowlistByTopic, "allowlistByTopic is required");

        final Map<String, Set<String>> copied = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : allowlistByTopic.entrySet()) {
            final String topic = normalize(entry.getKey());
            final Set<String> sourceSet = entry.getValue();
            if (topic == null) {
                continue;
            }
            if (sourceSet == null || sourceSet.isEmpty()) {
                copied.put(topic, Set.of());
                continue;
            }
            copied.put(topic, Set.copyOf(sourceSet));
        }
        this.allowlistByTopic = Map.copyOf(copied);

        log.info("KafkaSourceAllowlistPolicy initialized. managedTopicCount={}", this.allowlistByTopic.size());
    }

    /**
     * source 허용 여부를 검증합니다.
     *
     * @param topic 토픽명
     * @param source metadata.source 값
     * @return 실패 시 실패 정보, 성공 시 빈 Optional
     */
    public Optional<KafkaValidationFailure> validate(final String topic, final String source) {
        final String normalizedTopic = normalize(topic);
        if (normalizedTopic == null || !TcKafkaTopics.isSupported(normalizedTopic)) {
            return Optional.of(new KafkaValidationFailure(
                    KafkaValidationErrorCode.INVALID_TOPIC,
                    KafkaValidationDisposition.REJECTED,
                    "unsupported topic: " + topic
            ));
        }

        final String normalizedSource = normalize(source);
        if (normalizedSource == null) {
            return Optional.of(new KafkaValidationFailure(
                    KafkaValidationErrorCode.MISSING_SOURCE,
                    KafkaValidationDisposition.REJECTED,
                    "source is required"
            ));
        }

        final Set<String> allowlist = allowlistByTopic.getOrDefault(normalizedTopic, Set.of());
        if (!allowlist.contains(normalizedSource)) {
            if (log.isDebugEnabled()) {
                log.debug("source allowlist validation failed. topic={}, source={}, allowlist={}",
                        normalizedTopic, normalizedSource, allowlist);
            }
            return Optional.of(new KafkaValidationFailure(
                    KafkaValidationErrorCode.SOURCE_NOT_ALLOWED,
                    KafkaValidationDisposition.REJECTED,
                    "source is not allowed for topic. topic=" + normalizedTopic + ", source=" + normalizedSource
            ));
        }

        if (log.isDebugEnabled()) {
            log.debug("source allowlist validation passed. topic={}, source={}", normalizedTopic, normalizedSource);
        }
        return Optional.empty();
    }

    /**
     * 토픽별 허용 source 목록을 조회합니다.
     *
     * @param topic 토픽명
     * @return 허용 source 불변 집합
     */
    public Set<String> allowedSources(final String topic) {
        final String normalizedTopic = normalize(topic);
        if (normalizedTopic == null) {
            return Set.of();
        }
        return allowlistByTopic.getOrDefault(normalizedTopic, Set.of());
    }

    /**
     * 문자열을 trim 기반으로 정규화합니다.
     *
     * @param value 원본 문자열
     * @return 공백 제거 문자열, 값이 없으면 null
     */
    private static String normalize(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }
}
