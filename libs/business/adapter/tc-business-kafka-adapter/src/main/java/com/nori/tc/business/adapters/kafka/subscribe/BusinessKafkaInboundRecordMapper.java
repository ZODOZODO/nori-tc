package com.nori.tc.business.adapters.kafka.subscribe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.adapters.kafka.contract.BusinessKafkaContractSupport;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.runtime.BusinessMessageType;
import com.nori.tc.messaging.domain.kafka.TcKafkaTopics;
import com.nori.tc.messaging.domain.kafka.contract.TcCommonKafkaMetadata;
import com.nori.tc.messaging.domain.kafka.contract.TcKafkaEnvelope;
import com.nori.tc.messaging.domain.kafka.contract.TcKafkaMetadata;
import com.nori.tc.messaging.domain.kafka.contract.TcMesKafkaMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Kafka 원문 레코드(JSON)를 Business 런타임 입력 모델로 변환하는 매퍼입니다.
 *
 * <p>변환 규칙:
 * 1) messageName = metadata.eventType
 * 2) eqpId = data.eqpId 우선 추출
 * 3) payloadRef = topic:partition:offset
 * 4) metadata/key 정책 검증 후 런타임으로 전달</p>
 */
@Component
public class BusinessKafkaInboundRecordMapper {

    private static final Logger log = LoggerFactory.getLogger(BusinessKafkaInboundRecordMapper.class);

    private final ObjectMapper objectMapper;
    private final BusinessKafkaContractSupport contractSupport;

    /**
     * JSON 파서와 Kafka 계약 검증 지원기를 주입받습니다.
     *
     * @param objectMapper JSON object mapper
     * @param contractSupport Kafka 계약 검증/키 정책 지원기
     */
    public BusinessKafkaInboundRecordMapper(
            final ObjectMapper objectMapper,
            final BusinessKafkaContractSupport contractSupport
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is null");
        this.contractSupport = Objects.requireNonNull(contractSupport, "contractSupport is null");
    }

    /**
     * Kafka 레코드를 Business 런타임 입력 레코드로 변환합니다.
     *
     * @param record Kafka 원본 레코드
     * @param messageType Business 메시지 타입(EQP/MES/UI)
     * @return 변환된 런타임 입력 레코드
     * @throws Exception 파싱/필수값 검증 실패
     */
    public BusinessInboundRecord map(
            final ConsumerRecord<String, String> record,
            final BusinessMessageType messageType
    ) throws Exception {
        Objects.requireNonNull(record, "record is null");
        Objects.requireNonNull(messageType, "messageType is null");

        final String payload = normalize(record.value());
        if (payload == null) {
            throw new IllegalArgumentException("Kafka payload is required");
        }

        final JsonNode root = objectMapper.readTree(payload);
        final JsonNode metadataNode = requireObjectNode(root, "metadata");
        final JsonNode dataNode = requireObjectNode(root, "data");

        final TcKafkaEnvelope<? extends TcKafkaMetadata, JsonNode> envelope = createEnvelope(
                record.topic(),
                metadataNode,
                dataNode
        );

        // 5) 공통 envelope/metadata 검증을 ingress 경로에 연결합니다.
        contractSupport.validateEnvelope(record.topic(), envelope);

        final String eqpId = resolveEqpId(record, root, dataNode, messageType);
        if (eqpId == null) {
            throw new IllegalArgumentException("eqpId is required in Kafka payload");
        }

        // 6) Kafka key 정책(MES=correlationId, 그 외=eqpId) 준수 여부를 검증합니다.
        contractSupport.verifyKafkaRecordKey(
                record.topic(),
                record.key(),
                envelope.metadata(),
                eqpId
        );

        final String payloadRef = record.topic() + ":" + record.partition() + ":" + record.offset();
        final BusinessInboundRecord mapped = new BusinessInboundRecord(
                record.topic(),
                record.partition(),
                record.offset(),
                eqpId,
                messageType,
                envelope.metadata().eventType(),
                payloadRef,
                payload
        );

        if (log.isDebugEnabled()) {
            log.debug("Kafka inbound record mapped. topic={}, key={}, eqpId={}, eventType={}, partition={}, offset={}",
                    record.topic(),
                    record.key(),
                    mapped.eqpId(),
                    mapped.messageName(),
                    mapped.partition(),
                    mapped.offset());
        }
        return mapped;
    }

    /**
     * 토픽 타입(MES/비-MES)에 맞춰 metadata 구현 타입을 선택하고 envelope를 생성합니다.
     *
     * @param topic 수신 토픽
     * @param metadataNode metadata 노드
     * @param dataNode data 노드
     * @return 생성된 envelope
     */
    private TcKafkaEnvelope<? extends TcKafkaMetadata, JsonNode> createEnvelope(
            final String topic,
            final JsonNode metadataNode,
            final JsonNode dataNode
    ) {
        final String eventType = firstText(metadataNode, "eventType");
        final String timestamp = firstText(metadataNode, "timestamp");
        final String source = firstText(metadataNode, "source");
        final String schemaVersion = firstText(metadataNode, "schemaVersion");

        if (TcKafkaTopics.isMesTopic(topic)) {
            final TcMesKafkaMetadata metadata = contractSupport.createMesMetadata(
                    eventType,
                    timestamp,
                    source,
                    firstText(metadataNode, "correlationId"),
                    schemaVersion
            );
            return new TcKafkaEnvelope<>(metadata, dataNode);
        }

        final TcCommonKafkaMetadata metadata = contractSupport.createCommonMetadata(
                eventType,
                timestamp,
                source,
                firstText(metadataNode, "traceId"),
                schemaVersion
        );
        return new TcKafkaEnvelope<>(metadata, dataNode);
    }

    /**
     * EQP ID를 data/root/key 순서로 해석합니다.
     *
     * <p>MES 토픽은 key가 correlationId일 수 있으므로 key fallback을 사용하지 않습니다.</p>
     */
    private String resolveEqpId(
            final ConsumerRecord<String, String> record,
            final JsonNode root,
            final JsonNode data,
            final BusinessMessageType messageType
    ) {
        final String fromData = firstText(data, "eqpId", "eqpid", "equipmentId");
        if (fromData != null) {
            return fromData;
        }

        final String fromRoot = firstText(root, "eqpId", "eqpid", "equipmentId");
        if (fromRoot != null) {
            return fromRoot;
        }

        if (messageType != BusinessMessageType.MES) {
            final String fromKey = normalize(record.key());
            if (fromKey != null) {
                if (log.isDebugEnabled()) {
                    log.debug("eqpId fallback to Kafka key. topic={}, partition={}, offset={}, key={}",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            fromKey);
                }
                return fromKey;
            }
        }

        return null;
    }

    /**
     * 루트 JSON에서 필수 object 노드를 추출합니다.
     *
     * @param root 루트 JSON 노드
     * @param fieldName 필수 필드명
     * @return object 노드
     */
    private static JsonNode requireObjectNode(final JsonNode root, final String fieldName) {
        final JsonNode node = root.path(fieldName);
        if (node.isMissingNode() || node.isNull() || !node.isObject()) {
            throw new IllegalArgumentException(fieldName + " object is required");
        }
        return node;
    }

    /**
     * firstText 기능을 수행합니다.
     *
     * @param node 입력 값
     * @param fields 입력 값
     * @return 처리 결과
     */

    private static String firstText(final JsonNode node, final String... fields) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        for (String field : fields) {
            final JsonNode child = node.path(field);
            final String value = normalize(child.asText(null));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * normalize 기능을 수행합니다.
     *
     * @param value 입력 값
     * @return 처리 결과
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
