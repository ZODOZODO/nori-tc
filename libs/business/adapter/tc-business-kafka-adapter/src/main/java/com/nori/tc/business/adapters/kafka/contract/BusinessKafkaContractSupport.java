package com.nori.tc.business.adapters.kafka.contract;

import com.nori.tc.messaging.domain.kafka.TcKafkaSources;
import com.nori.tc.messaging.domain.kafka.TcKafkaTopics;
import com.nori.tc.messaging.domain.kafka.contract.TcCommonKafkaMetadata;
import com.nori.tc.messaging.domain.kafka.contract.TcKafkaEnvelope;
import com.nori.tc.messaging.domain.kafka.contract.TcKafkaMetadata;
import com.nori.tc.messaging.domain.kafka.contract.TcMesKafkaMetadata;
import com.nori.tc.messaging.domain.kafka.key.KafkaMessageKeyResolver;
import com.nori.tc.messaging.domain.kafka.policy.KafkaEventTypeNamingPolicy;
import com.nori.tc.messaging.domain.kafka.policy.KafkaSchemaVersionPolicy;
import com.nori.tc.messaging.domain.kafka.policy.KafkaSourceAllowlistPolicy;
import com.nori.tc.messaging.domain.kafka.validation.KafkaMetadataValidator;
import com.nori.tc.messaging.domain.kafka.validation.KafkaValidationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Business Kafka 어댑터의 공통 계약 검증 지원 클래스입니다.
 *
 * <p>역할:
 * 1) {@link TcKafkaEnvelope} 생성 시 사용할 metadata 정규화
 * 2) {@link KafkaMetadataValidator} 기반 메타데이터 검증
 * 3) {@link KafkaMessageKeyResolver} 기반 Kafka key 정책 적용/검증</p>
 *
 * <p>참고:
 * source 값은 운영 중 기존 `*-APP` 접미사가 섞여 들어올 수 있으므로,
 * 검증 전에 canonical source(접미사 제거)로 정규화합니다.</p>
 */
@Component
public class BusinessKafkaContractSupport {

    private static final Logger log = LoggerFactory.getLogger(BusinessKafkaContractSupport.class);
    private static final String DEFAULT_SCHEMA_VERSION = "v1";
    private static final String APP_SUFFIX = "-APP";

    private final KafkaMetadataValidator metadataValidator;
    private final KafkaMessageKeyResolver keyResolver;

    /**
     * 공통 계약 검증기/키 해석기를 초기화합니다.
     */
    public BusinessKafkaContractSupport() {
        this.metadataValidator = new KafkaMetadataValidator(
                new KafkaEventTypeNamingPolicy(),
                new KafkaSourceAllowlistPolicy(buildAllowlist()),
                KafkaSchemaVersionPolicy.defaultPolicy()
        );
        this.keyResolver = new KafkaMessageKeyResolver();

        log.info("BusinessKafkaContractSupport initialized. supportedTopics={}", TcKafkaTopics.allTopics());
    }

    /**
     * 비-MES 토픽용 공통 metadata를 생성합니다.
     *
     * @param eventType 이벤트 타입
     * @param timestamp 이벤트 시각
     * @param source 발행 source
     * @param traceId traceId
     * @param schemaVersion schema version(없으면 v1 기본값)
     * @return 공통 metadata
     */
    public TcCommonKafkaMetadata createCommonMetadata(
            final String eventType,
            final String timestamp,
            final String source,
            final String traceId,
            final String schemaVersion
    ) {
        return new TcCommonKafkaMetadata(
                normalizeRequired("eventType", eventType),
                normalizeRequired("timestamp", timestamp),
                normalizeSource(source),
                normalizeRequired("traceId", traceId),
                normalizeSchemaVersion(schemaVersion)
        );
    }

    /**
     * MES 토픽용 metadata를 생성합니다.
     *
     * @param eventType 이벤트 타입
     * @param timestamp 이벤트 시각
     * @param source 발행 source
     * @param correlationId correlationId
     * @param schemaVersion schema version(없으면 v1 기본값)
     * @return MES metadata
     */
    public TcMesKafkaMetadata createMesMetadata(
            final String eventType,
            final String timestamp,
            final String source,
            final String correlationId,
            final String schemaVersion
    ) {
        return new TcMesKafkaMetadata(
                normalizeRequired("eventType", eventType),
                normalizeRequired("timestamp", timestamp),
                normalizeSource(source),
                normalizeRequired("correlationId", correlationId),
                normalizeSchemaVersion(schemaVersion)
        );
    }

    /**
     * envelope의 metadata를 검증합니다.
     *
     * @param topic 대상 토픽
     * @param envelope 검증 대상 envelope
     */
    public void validateEnvelope(
            final String topic,
            final TcKafkaEnvelope<? extends TcKafkaMetadata, ?> envelope
    ) {
        Objects.requireNonNull(envelope, "envelope is null");
        final List<KafkaValidationFailure> failures = metadataValidator.validate(topic, envelope.metadata());
        if (!failures.isEmpty()) {
            final String failureSummary = failures.stream()
                    .map(failure -> failure.errorCode() + ":" + failure.message())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("unknown");
            throw new IllegalArgumentException(
                    "Kafka metadata validation failed. topic=" + topic + ", failures=[" + failureSummary + "]"
            );
        }

        if (log.isDebugEnabled()) {
            log.debug("Kafka envelope metadata validated. topic={}, eventType={}, source={}, schemaVersion={}",
                    topic,
                    envelope.metadata().eventType(),
                    envelope.metadata().source(),
                    envelope.metadata().schemaVersion());
        }
    }

    /**
     * 토픽/metadata/eqpId 기준 Kafka key를 계산합니다.
     *
     * @param topic 대상 토픽
     * @param metadata envelope metadata
     * @param eqpId eqpId
     * @return 정책에 맞는 Kafka key
     */
    public String resolveKafkaKey(
            final String topic,
            final TcKafkaMetadata metadata,
            final String eqpId
    ) {
        return keyResolver.resolve(topic, metadata, eqpId);
    }

    /**
     * 수신 레코드 key가 정책에 맞는지 검증합니다.
     *
     * @param topic 수신 토픽
     * @param actualKey 수신 Kafka key
     * @param metadata 수신 metadata
     * @param eqpId 수신 payload eqpId
     */
    public void verifyKafkaRecordKey(
            final String topic,
            final String actualKey,
            final TcKafkaMetadata metadata,
            final String eqpId
    ) {
        final String expectedKey = resolveKafkaKey(topic, metadata, eqpId);
        final String normalizedActual = normalizeRequired("kafkaRecordKey", actualKey);
        if (!expectedKey.equals(normalizedActual)) {
            throw new IllegalArgumentException(
                    "Kafka key policy mismatch. topic="
                            + topic
                            + ", expected="
                            + expectedKey
                            + ", actual="
                            + normalizedActual
            );
        }

        log.debug("Kafka record key validated. topic={}, key={}", topic, normalizedActual);
    }

    /**
     * source를 canonical 값으로 정규화합니다.
     *
     * <p>예:
     * `TC-BUSINESS-CORE-APP` -> `TC-BUSINESS-CORE`</p>
     *
     * @param source 원본 source
     * @return canonical source
     */
    public String normalizeSource(final String source) {
        final String normalized = normalizeRequired("source", source);
        if (!normalized.endsWith(APP_SUFFIX)) {
            return normalized;
        }

        final String canonical = normalized.substring(0, normalized.length() - APP_SUFFIX.length());
        log.debug("Kafka source normalized from app alias. original={}, canonical={}", normalized, canonical);

        return canonical;
    }

    /**
     * normalizeSchemaVersion 기능을 수행합니다.
     *
     * @param schemaVersion 입력 값
     * @return 처리 결과
     */

    private static String normalizeSchemaVersion(final String schemaVersion) {
        final String normalized = normalizeNullable(schemaVersion);
        if (normalized == null) {
            return DEFAULT_SCHEMA_VERSION;
        }
        return normalized;
    }

    /**
     * normalizeRequired 기능을 수행합니다.
     *
     * @param field 입력 값
     * @param value 입력 값
     * @return 처리 결과
     */

    private static String normalizeRequired(final String field, final String value) {
        final String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    /**
     * normalizeNullable 기능을 수행합니다.
     *
     * @param value 입력 값
     * @return 처리 결과
     */

    private static String normalizeNullable(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }

    /**
     * source allowlist를 구성합니다.
     *
     * <p>설계 canonical source + 운영 호환용 `*-APP` alias를 함께 허용합니다.</p>
     */
    private static Map<String, Set<String>> buildAllowlist() {
        final Map<String, Set<String>> allowlist = new LinkedHashMap<>();

        allowlist.put(TcKafkaTopics.EQP_EVENTS, Set.of(
                TcKafkaSources.COMM_GATEWAY,
                TcKafkaSources.COMM_GATEWAY + APP_SUFFIX
        ));
        allowlist.put(TcKafkaTopics.EQP_COMMANDS, Set.of(
                TcKafkaSources.BUSINESS_CORE,
                TcKafkaSources.BUSINESS_CORE + APP_SUFFIX
        ));
        allowlist.put(TcKafkaTopics.MES_EVENTS, Set.of(
                TcKafkaSources.EXTERNAL_MES_ADAPTER,
                TcKafkaSources.EXTERNAL_MES_ADAPTER + APP_SUFFIX
        ));
        allowlist.put(TcKafkaTopics.MES_COMMANDS, Set.of(
                TcKafkaSources.BUSINESS_CORE,
                TcKafkaSources.BUSINESS_CORE + APP_SUFFIX
        ));
        allowlist.put(TcKafkaTopics.UI_EVENTS, Set.of(
                TcKafkaSources.UI_BACKEND,
                TcKafkaSources.UI_BACKEND + APP_SUFFIX
        ));
        allowlist.put(TcKafkaTopics.UI_COMMANDS, Set.of(
                TcKafkaSources.BUSINESS_CORE,
                TcKafkaSources.BUSINESS_CORE + APP_SUFFIX,
                TcKafkaSources.COMM_GATEWAY,
                TcKafkaSources.COMM_GATEWAY + APP_SUFFIX
        ));

        return allowlist;
    }
}
