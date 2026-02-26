package com.nori.tc.comm.adapters.kafka.contract;

import com.nori.tc.messaging.domain.kafka.TcKafkaSources;
import com.nori.tc.messaging.domain.kafka.TcKafkaTopics;
import com.nori.tc.messaging.domain.kafka.contract.TcCommonKafkaMetadata;
import com.nori.tc.messaging.domain.kafka.contract.TcKafkaEnvelope;
import com.nori.tc.messaging.domain.kafka.contract.TcKafkaMetadata;
import com.nori.tc.messaging.domain.kafka.key.KafkaMessageKeyResolver;
import com.nori.tc.messaging.domain.kafka.policy.KafkaEventTypeNamingPolicy;
import com.nori.tc.messaging.domain.kafka.policy.KafkaSchemaVersionPolicy;
import com.nori.tc.messaging.domain.kafka.policy.KafkaSourceAllowlistPolicy;
import com.nori.tc.messaging.domain.kafka.validation.KafkaMetadataValidator;
import com.nori.tc.messaging.domain.kafka.validation.KafkaValidationFailure;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskReplyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Gateway Kafka 어댑터에서 사용하는 공통 계약 검증 지원 컴포넌트입니다.
 *
 * <p>핵심 역할:
 * 1) metadata/data envelope 구조 검증
 * 2) source/eventType/schemaVersion 정책 검증
 * 3) topic 별 Kafka key 정책(MES=correlationId, 그 외=eqpId) 검증</p>
 *
 * <p>Gateway는 현재 EQP/UI 토픽만 직접 사용하지만, 정책 일관성을 위해
 * 전체 토픽 allowlist를 동일 기준으로 구성합니다.</p>
 */
@Component
public class GatewayKafkaContractSupport {

    private static final Logger log = LoggerFactory.getLogger(GatewayKafkaContractSupport.class);
    private static final String DEFAULT_SCHEMA_VERSION = "v1";
    private static final String APP_SUFFIX = "-APP";

    private final KafkaMetadataValidator metadataValidator;
    private final KafkaMessageKeyResolver keyResolver;

    /**
     * 공통 validator/key resolver를 초기화합니다.
     */
    public GatewayKafkaContractSupport() {
        // U1 설계 기준:
        // Gateway는 분리된 UI 이벤트 토픽(`tc.ui.events.gateway`)만 수신 대상으로 검증합니다.
        final Map<String, Set<String>> allowlist = buildAllowlist();
        this.metadataValidator = new KafkaMetadataValidator(
                new KafkaEventTypeNamingPolicy(),
                new KafkaSourceAllowlistPolicy(allowlist),
                KafkaSchemaVersionPolicy.defaultPolicy()
        );
        this.keyResolver = new KafkaMessageKeyResolver();

        log.info("GatewayKafkaContractSupport initialized. supportedTopics={}, gatewayUiEventsTopic={}",
                TcKafkaTopics.allTopics(),
                TcKafkaTopics.UI_EVENTS_GATEWAY);
        if (log.isDebugEnabled()) {
            log.debug("Gateway Kafka source allowlist configured for UI gateway topic. topic={}, allowedSources={}",
                    TcKafkaTopics.UI_EVENTS_GATEWAY,
                    allowlist.get(TcKafkaTopics.UI_EVENTS_GATEWAY));
        }
    }

    /**
     * Business -> Gateway command 레코드의 계약을 검증합니다.
     *
     * @param topic 수신 topic
     * @param kafkaKey Kafka record key
     * @param message command payload
     * @return 검증된 공통 metadata
     */
    public TcCommonKafkaMetadata validateGatewayBusinessCommandRecord(
            final String topic,
            final String kafkaKey,
            final GatewayBusinessCommandMessage message
    ) {
        Objects.requireNonNull(message, "message is null");
        if (message.metadata() == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        if (message.data() == null) {
            throw new IllegalArgumentException("data is required");
        }

        final TcCommonKafkaMetadata metadata = createCommonMetadata(
                message.metadata().eventType(),
                message.metadata().timestamp(),
                message.metadata().source(),
                message.metadata().traceId(),
                null
        );

        validateEnvelope(topic, new TcKafkaEnvelope<>(metadata, message.data()));
        verifyKafkaRecordKey(topic, kafkaKey, metadata, message.data().eqpId());
        return metadata;
    }

    /**
     * Gateway -> Business event 레코드의 계약을 검증합니다.
     *
     * @param topic 발행 topic
     * @param kafkaKey Kafka record key
     * @param message event payload
     * @return 검증된 공통 metadata
     */
    public TcCommonKafkaMetadata validateGatewayBusinessEventRecord(
            final String topic,
            final String kafkaKey,
            final GatewayBusinessEventMessage message
    ) {
        Objects.requireNonNull(message, "message is null");
        if (message.metadata() == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        if (message.data() == null) {
            throw new IllegalArgumentException("data is required");
        }

        final TcCommonKafkaMetadata metadata = createCommonMetadata(
                message.metadata().eventType(),
                message.metadata().timestamp(),
                message.metadata().source(),
                message.metadata().traceId(),
                null
        );
        final String eqpId = extractEqpIdFromGatewayEventData(message.data());

        validateEnvelope(topic, new TcKafkaEnvelope<>(metadata, message.data()));
        verifyKafkaRecordKey(topic, kafkaKey, metadata, eqpId);
        return metadata;
    }

    /**
     * UI 이벤트 수신 레코드의 계약을 검증합니다.
     *
     * @param topic 수신 topic
     * @param kafkaKey Kafka record key
     * @param message UI task 요청 payload
     * @return 검증된 공통 metadata
     */
    public TcCommonKafkaMetadata validateUiTaskEventRecord(
            final String topic,
            final String kafkaKey,
            final KafkaUiTaskMessage message
    ) {
        Objects.requireNonNull(message, "message is null");
        if (message.metadata() == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        if (message.data() == null) {
            throw new IllegalArgumentException("data is required");
        }

        final TcCommonKafkaMetadata metadata = createCommonMetadata(
                message.metadata().eventType(),
                message.metadata().timestamp(),
                message.metadata().source(),
                message.metadata().traceId(),
                null
        );

        validateEnvelope(topic, new TcKafkaEnvelope<>(metadata, message.data()));
        verifyKafkaRecordKey(topic, kafkaKey, metadata, message.data().eqpId());
        return metadata;
    }

    /**
     * UI 명령 발행 레코드의 계약을 검증합니다.
     *
     * @param topic 발행 topic
     * @param kafkaKey Kafka record key
     * @param message UI reply payload
     * @return 검증된 공통 metadata
     */
    public TcCommonKafkaMetadata validateUiTaskCommandRecord(
            final String topic,
            final String kafkaKey,
            final KafkaUiTaskReplyMessage message
    ) {
        Objects.requireNonNull(message, "message is null");
        if (message.metadata() == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        if (message.data() == null) {
            throw new IllegalArgumentException("data is required");
        }

        final TcCommonKafkaMetadata metadata = createCommonMetadata(
                message.metadata().eventType(),
                message.metadata().timestamp(),
                message.metadata().source(),
                message.metadata().traceId(),
                null
        );

        validateEnvelope(topic, new TcKafkaEnvelope<>(metadata, message.data()));
        verifyKafkaRecordKey(topic, kafkaKey, metadata, message.data().eqpId());
        return metadata;
    }

    /**
     * 공통 metadata를 생성합니다.
     *
     * @param eventType 이벤트 타입
     * @param timestamp ISO-8601 타임스탬프
     * @param source 발행 source
     * @param traceId 추적 ID
     * @param schemaVersion 스키마 버전(null이면 v1)
     * @return 정규화된 공통 metadata
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
     * metadata 정책을 검증합니다.
     *
     * @param topic 대상 topic
     * @param envelope 검증할 envelope
     */
    public void validateEnvelope(
            final String topic,
            final TcKafkaEnvelope<? extends TcKafkaMetadata, ?> envelope
    ) {
        Objects.requireNonNull(envelope, "envelope is null");
        final List<KafkaValidationFailure> failures = metadataValidator.validate(topic, envelope.metadata());
        if (!failures.isEmpty()) {
            final String summary = failures.stream()
                    .map(failure -> failure.errorCode() + ":" + failure.message())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("unknown");
            throw new IllegalArgumentException(
                    "Kafka metadata validation failed. topic=" + topic + ", failures=[" + summary + "]"
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
     * topic/metadata/eqpId 기준 Kafka key를 계산합니다.
     *
     * @param topic 대상 topic
     * @param metadata metadata
     * @param eqpId 설비 ID
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
     * Kafka record key 정책 일치를 검증합니다.
     *
     * @param topic 대상 topic
     * @param actualKey 실제 Kafka key
     * @param metadata metadata
     * @param eqpId 설비 ID
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

        if (log.isDebugEnabled()) {
            log.debug("Kafka record key validated. topic={}, key={}", topic, normalizedActual);
        }
    }

    /**
     * source를 canonical 값으로 정규화합니다.
     *
     * <p>예: {@code TC-COMM-GATEWAY-APP -> TC-COMM-GATEWAY}</p>
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
        if (log.isDebugEnabled()) {
            log.debug("Kafka source normalized from app alias. original={}, canonical={}", normalized, canonical);
        }
        return canonical;
    }

    /**
     * Gateway event data에서 eqpId를 추출합니다.
     *
     * @param data protocol 별 event data
     * @return eqpId
     */
    private static String extractEqpIdFromGatewayEventData(
            final GatewayBusinessEventMessage.GatewayBusinessEventData data
    ) {
        if (data instanceof GatewayBusinessEventMessage.GatewayBusinessSocketEventData socketData) {
            return socketData.eqpId();
        }
        if (data instanceof GatewayBusinessEventMessage.GatewayBusinessHsmsEventData hsmsData) {
            return hsmsData.eqpId();
        }
        throw new IllegalArgumentException("Unsupported GatewayBusinessEventData type");
    }

    /**
     * source allowlist를 구성합니다.
     *
     * <p>운영 호환을 위해 canonical source와 {@code -APP} alias를 모두 허용합니다.</p>
     *
     * @return topic 별 source allowlist
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
        // U1 설계 기준:
        // Gateway 계약 검증에서는 Gateway 대상 UI 이벤트 토픽만 허용 대상으로 등록합니다.
        allowlist.put(TcKafkaTopics.UI_EVENTS_GATEWAY, Set.of(
                TcKafkaSources.UI_BACKEND,
                TcKafkaSources.UI_BACKEND + APP_SUFFIX
        ));
        allowlist.put(TcKafkaTopics.UI_COMMANDS, Set.of(
                TcKafkaSources.COMM_GATEWAY,
                TcKafkaSources.COMM_GATEWAY + APP_SUFFIX,
                TcKafkaSources.BUSINESS_CORE,
                TcKafkaSources.BUSINESS_CORE + APP_SUFFIX
        ));

        return allowlist;
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
}
