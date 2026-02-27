package com.nori.tc.messaging.domain.kafka.validation;

import com.nori.tc.messaging.domain.kafka.TcKafkaTopics;
import com.nori.tc.messaging.domain.kafka.contract.TcCommonKafkaMetadata;
import com.nori.tc.messaging.domain.kafka.contract.TcKafkaMetadata;
import com.nori.tc.messaging.domain.kafka.contract.TcMesKafkaMetadata;
import com.nori.tc.messaging.domain.kafka.policy.KafkaEventTypeNamingPolicy;
import com.nori.tc.messaging.domain.kafka.policy.KafkaSchemaVersionPolicy;
import com.nori.tc.messaging.domain.kafka.policy.KafkaSourceAllowlistPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Kafka metadata 통합 검증기입니다.
 *
 * <p>검증 범위:
 * 1) eventType 네이밍 규칙
 * 2) source allowlist
 * 3) schemaVersion 정책
 * 4) 토픽별 식별자 규칙(traceId / correlationId)</p>
 */
public final class KafkaMetadataValidator {

    private static final Logger log = LoggerFactory.getLogger(KafkaMetadataValidator.class);

    /**
     * eventType 정책입니다.
     */
    private final KafkaEventTypeNamingPolicy eventTypePolicy;

    /**
     * source allowlist 정책입니다.
     */
    private final KafkaSourceAllowlistPolicy sourcePolicy;

    /**
     * schemaVersion 정책입니다.
     */
    private final KafkaSchemaVersionPolicy schemaVersionPolicy;

    /**
     * 기본 정책 조합으로 검증기를 생성합니다.
     */
    public KafkaMetadataValidator() {
        this(
                new KafkaEventTypeNamingPolicy(),
                KafkaSourceAllowlistPolicy.defaultPolicy(),
                KafkaSchemaVersionPolicy.defaultPolicy()
        );
    }

    /**
     * 사용자 정의 정책 조합으로 검증기를 생성합니다.
     *
     * @param eventTypePolicy eventType 정책
     * @param sourcePolicy source allowlist 정책
     * @param schemaVersionPolicy schemaVersion 정책
     */
    public KafkaMetadataValidator(
            final KafkaEventTypeNamingPolicy eventTypePolicy,
            final KafkaSourceAllowlistPolicy sourcePolicy,
            final KafkaSchemaVersionPolicy schemaVersionPolicy
    ) {
        this.eventTypePolicy = Objects.requireNonNull(eventTypePolicy, "eventTypePolicy is required");
        this.sourcePolicy = Objects.requireNonNull(sourcePolicy, "sourcePolicy is required");
        this.schemaVersionPolicy = Objects.requireNonNull(schemaVersionPolicy, "schemaVersionPolicy is required");

        log.info("KafkaMetadataValidator initialized.");
    }

    /**
     * topic + metadata 조합을 검증합니다.
     *
     * @param topic 대상 토픽
     * @param metadata 검증 대상 metadata
     * @return 검증 실패 목록(비어 있으면 통과)
     */
    public List<KafkaValidationFailure> validate(final String topic, final TcKafkaMetadata metadata) {
        final List<KafkaValidationFailure> failures = new ArrayList<>();

        if (topic == null || topic.isBlank() || !TcKafkaTopics.isSupported(topic)) {
            failures.add(new KafkaValidationFailure(
                    KafkaValidationErrorCode.INVALID_TOPIC,
                    KafkaValidationDisposition.REJECTED,
                    "unsupported topic: " + topic
            ));
            return List.copyOf(failures);
        }

        if (metadata == null) {
            failures.add(new KafkaValidationFailure(
                    KafkaValidationErrorCode.MISSING_METADATA,
                    KafkaValidationDisposition.REJECTED,
                    "metadata is required"
            ));
            return List.copyOf(failures);
        }

        eventTypePolicy.validate(metadata.eventType()).ifPresent(failures::add);
        sourcePolicy.validate(topic, metadata.source()).ifPresent(failures::add);
        schemaVersionPolicy.validate(topic, metadata.schemaVersion()).ifPresent(failures::add);

        if (TcKafkaTopics.isMesTopic(topic)) {
            validateMesMetadata(metadata, failures);
        } else {
            validateCommonMetadata(metadata, failures);
        }

        if (failures.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("Kafka metadata validation passed. topic={}, eventType={}, source={}, schemaVersion={}",
                        topic, metadata.eventType(), metadata.source(), metadata.schemaVersion());
            }
            return List.of();
        }

        if (log.isDebugEnabled()) {
            log.debug("Kafka metadata validation failed. topic={}, failCount={}, failCodes={}",
                    topic,
                    failures.size(),
                    failures.stream().map(f -> f.errorCode().name()).toList());
        }
        return List.copyOf(failures);
    }

    /**
     * MES 토픽 metadata의 식별자(correlationId) 규칙을 검증합니다.
     *
     * @param metadata 검증 대상 metadata
     * @param failures 누적 실패 목록
     */
    private void validateMesMetadata(final TcKafkaMetadata metadata, final List<KafkaValidationFailure> failures) {
        if (!(metadata instanceof TcMesKafkaMetadata mesMetadata)) {
            failures.add(new KafkaValidationFailure(
                    KafkaValidationErrorCode.METADATA_TYPE_MISMATCH,
                    KafkaValidationDisposition.REJECTED,
                    "MES topic requires TcMesKafkaMetadata"
            ));
            return;
        }

        if (mesMetadata.correlationId() == null || mesMetadata.correlationId().isBlank()) {
            failures.add(new KafkaValidationFailure(
                    KafkaValidationErrorCode.MISSING_CORRELATION_ID,
                    KafkaValidationDisposition.REJECTED,
                    "correlationId is required for MES topic"
            ));
        }
    }

    /**
     * 비-MES 토픽 metadata의 식별자(traceId) 규칙을 검증합니다.
     *
     * @param metadata 검증 대상 metadata
     * @param failures 누적 실패 목록
     */
    private void validateCommonMetadata(final TcKafkaMetadata metadata, final List<KafkaValidationFailure> failures) {
        if (!(metadata instanceof TcCommonKafkaMetadata commonMetadata)) {
            failures.add(new KafkaValidationFailure(
                    KafkaValidationErrorCode.METADATA_TYPE_MISMATCH,
                    KafkaValidationDisposition.REJECTED,
                    "non-MES topic requires TcCommonKafkaMetadata"
            ));
            return;
        }

        if (commonMetadata.traceId() == null || commonMetadata.traceId().isBlank()) {
            failures.add(new KafkaValidationFailure(
                    KafkaValidationErrorCode.MISSING_TRACE_ID,
                    KafkaValidationDisposition.REJECTED,
                    "traceId is required for non-MES topic"
            ));
        }
    }
}
