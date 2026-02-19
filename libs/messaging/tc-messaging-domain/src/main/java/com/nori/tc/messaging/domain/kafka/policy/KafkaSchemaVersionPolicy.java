package com.nori.tc.messaging.domain.kafka.policy;

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
import java.util.regex.Pattern;

/**
 * schemaVersion 유효성 정책입니다.
 *
 * <p>schemaVersion을 강제하면 다음 운영 이점을 얻을 수 있습니다.</p>
 * <p>1) 소비자/발행자의 계약 버전 불일치를 조기에 감지할 수 있습니다.</p>
 * <p>2) 동일 토픽의 계약 변경 시 점진 이행 구간을 정책으로 관리할 수 있습니다.</p>
 * <p>3) 장애 분석 시 "어떤 버전 메시지에서 실패했는지"를 즉시 추적할 수 있습니다.</p>
 */
public final class KafkaSchemaVersionPolicy {

    /**
     * schemaVersion 표현 형식입니다. 예: v1, v2
     */
    private static final Pattern VERSION_FORMAT = Pattern.compile("^v\\d+$");

    private static final Logger log = LoggerFactory.getLogger(KafkaSchemaVersionPolicy.class);

    /**
     * 토픽별 허용 schemaVersion 집합입니다.
     */
    private final Map<String, Set<String>> allowedVersionsByTopic;

    /**
     * 기본 정책(v1만 허용)을 생성합니다.
     *
     * <p>초기 전환 안정화를 위해 v1의 별칭 1도 함께 허용합니다.</p>
     *
     * @return 기본 schemaVersion 정책 객체
     */
    public static KafkaSchemaVersionPolicy defaultPolicy() {
        final Map<String, Set<String>> mapping = new LinkedHashMap<>();
        for (String topic : TcKafkaTopics.allTopics()) {
            mapping.put(topic, Set.of("v1", "1"));
        }
        return new KafkaSchemaVersionPolicy(mapping);
    }

    /**
     * 사용자 정의 토픽별 허용 버전 매핑으로 정책 객체를 생성합니다.
     *
     * @param allowedVersionsByTopic 토픽별 허용 버전
     */
    public KafkaSchemaVersionPolicy(final Map<String, Set<String>> allowedVersionsByTopic) {
        Objects.requireNonNull(allowedVersionsByTopic, "allowedVersionsByTopic is required");

        final Map<String, Set<String>> copied = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : allowedVersionsByTopic.entrySet()) {
            final String topic = normalize(entry.getKey());
            if (topic == null) {
                continue;
            }
            final Set<String> rawVersions = entry.getValue();
            if (rawVersions == null || rawVersions.isEmpty()) {
                copied.put(topic, Set.of());
                continue;
            }
            copied.put(topic, Set.copyOf(rawVersions));
        }
        this.allowedVersionsByTopic = Map.copyOf(copied);

        log.info("KafkaSchemaVersionPolicy initialized. managedTopicCount={}", this.allowedVersionsByTopic.size());
    }

    /**
     * schemaVersion을 검증합니다.
     *
     * @param topic 토픽명
     * @param schemaVersion schemaVersion 값
     * @return 실패 시 실패 정보, 성공 시 빈 Optional
     */
    public Optional<KafkaValidationFailure> validate(final String topic, final String schemaVersion) {
        final String normalizedTopic = normalize(topic);
        if (normalizedTopic == null || !TcKafkaTopics.isSupported(normalizedTopic)) {
            return Optional.of(new KafkaValidationFailure(
                    KafkaValidationErrorCode.INVALID_TOPIC,
                    KafkaValidationDisposition.REJECTED,
                    "unsupported topic: " + topic
            ));
        }

        final String normalizedVersion = normalize(schemaVersion);
        if (normalizedVersion == null) {
            return Optional.of(new KafkaValidationFailure(
                    KafkaValidationErrorCode.MISSING_SCHEMA_VERSION,
                    KafkaValidationDisposition.REJECTED,
                    "schemaVersion is required"
            ));
        }

        if (!VERSION_FORMAT.matcher(normalizedVersion).matches() && !"1".equals(normalizedVersion)) {
            return Optional.of(new KafkaValidationFailure(
                    KafkaValidationErrorCode.SCHEMA_VERSION_NOT_ALLOWED,
                    KafkaValidationDisposition.REJECTED,
                    "invalid schemaVersion format: " + normalizedVersion
            ));
        }

        final Set<String> allowedVersions = allowedVersionsByTopic.getOrDefault(normalizedTopic, Set.of());
        if (!allowedVersions.contains(normalizedVersion)) {
            if (log.isDebugEnabled()) {
                log.debug("schemaVersion validation failed. topic={}, schemaVersion={}, allowedVersions={}",
                        normalizedTopic, normalizedVersion, allowedVersions);
            }
            return Optional.of(new KafkaValidationFailure(
                    KafkaValidationErrorCode.SCHEMA_VERSION_NOT_ALLOWED,
                    KafkaValidationDisposition.REJECTED,
                    "schemaVersion is not allowed. topic=" + normalizedTopic + ", schemaVersion=" + normalizedVersion
            ));
        }

        if (log.isDebugEnabled()) {
            log.debug("schemaVersion validation passed. topic={}, schemaVersion={}", normalizedTopic, normalizedVersion);
        }
        return Optional.empty();
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

