package com.nori.tc.business.core.workflow.internal.matching;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 인바운드 payload(JSON)에서 워크플로우 평가/실행에 필요한 값을 추출하는 컴포넌트입니다.
 */
@Component
public class BusinessWorkflowPayloadExtractor {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowPayloadExtractor.class);

    private final ObjectMapper objectMapper;

    /**
     * 생성자에서 ObjectMapper 의존성을 주입받습니다.
     *
     * @param objectMapper JSON 파서
     */
    public BusinessWorkflowPayloadExtractor(final ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is null");
    }

    /**
     * payload를 message 변수 맵으로 변환합니다.
     *
     * @param payload 원본 payload 문자열
     * @return 파싱 성공 시 변수 맵, 실패 시 빈 맵
     */
    public Map<String, Object> extractMessageVariables(final String payload) {
        final String normalizedPayload = normalize(payload);
        if (normalizedPayload == null) {
            return Map.of();
        }

        try {
            final JsonNode root = objectMapper.readTree(normalizedPayload);
            final Object mapped = toValue(root);
            if (mapped instanceof Map<?, ?> rawMap) {
                final Map<String, Object> result = new LinkedHashMap<>();
                rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
                return Map.copyOf(result);
            }
            return Map.of("value", mapped);
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Payload JSON parsing failed while extracting message variables. payloadLength={}",
                        normalizedPayload.length(),
                        ex);
            }
            return Map.of();
        }
    }

    /**
     * payload에서 eventId를 우선순위 경로로 추출합니다.
     *
     * @param payload 원본 payload 문자열
     * @return eventId, 없으면 null
     */
    public String extractEventId(final String payload) {
        return extractPathText(payload,
                "eventId",
                "event_id",
                "data.eventId",
                "data.event_id",
                "secs2.eventId",
                "secs2.event_id"
        );
    }

    /**
     * payload에서 transactionId를 우선순위 경로로 추출합니다.
     *
     * @param payload 원본 payload 문자열
     * @return transactionId, 없으면 null
     */
    public String extractTransactionId(final String payload) {
        return extractPathText(payload,
                "transactionId",
                "transaction_id",
                "data.transactionId",
                "data.transaction_id",
                "secs2.transactionId",
                "secs2.transaction_id"
        );
    }

    /**
     * 필터 평가에 사용하는 컨텍스트 변수 맵을 구성합니다.
     *
     * @param record 인바운드 레코드
     * @return 불변 컨텍스트 변수 맵
     */
    public Map<String, Object> buildContextVariables(final BusinessInboundRecord record) {
        final Map<String, Object> context = new LinkedHashMap<>();
        context.put("topic", record.topic());
        context.put("partition", record.partition());
        context.put("offset", record.offset());
        context.put("eqpId", record.eqpId());
        context.put("messageType", record.messageType().name());
        context.put("messageName", record.messageName());
        context.put("payloadRef", record.payloadRef());
        return Map.copyOf(context);
    }

    /**
     * 주어진 경로 목록을 순서대로 탐색해 첫 번째 유효 텍스트 값을 반환합니다.
     */
    private String extractPathText(final String payload, final String... paths) {
        final String normalizedPayload = normalize(payload);
        if (normalizedPayload == null) {
            return null;
        }

        try {
            final JsonNode root = objectMapper.readTree(normalizedPayload);
            for (String path : paths) {
                final String value = pathText(root, path);
                if (value != null) {
                    return value;
                }
            }
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Payload JSON parsing failed while extracting path text. payloadLength={}",
                        normalizedPayload.length(),
                        ex);
            }
        }
        return null;
    }

    /**
     * dot-path(예: data.eventId)로 JSON 노드를 탐색해 텍스트를 추출합니다.
     */
    private static String pathText(final JsonNode root, final String path) {
        JsonNode cursor = root;
        for (String segment : path.split("\\.")) {
            if (cursor == null || cursor.isNull() || cursor.isMissingNode()) {
                return null;
            }
            cursor = cursor.path(segment);
        }
        if (cursor == null || cursor.isNull() || cursor.isMissingNode()) {
            return null;
        }
        return normalize(cursor.asText(null));
    }

    /**
     * JsonNode를 Java 값(Map/List/원시 타입)으로 재귀 변환합니다.
     */
    private static Object toValue(final JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isInt() || node.isLong()) {
            return node.asLong();
        }
        if (node.isFloat() || node.isDouble() || node.isBigDecimal()) {
            return node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray()) {
            final java.util.List<Object> values = new java.util.ArrayList<>();
            for (JsonNode child : node) {
                values.add(toValue(child));
            }
            return java.util.List.copyOf(values);
        }
        if (node.isObject()) {
            final Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                values.put(field.getKey(), toValue(field.getValue()));
            }
            return Map.copyOf(values);
        }
        return node.asText();
    }

    /**
     * 문자열을 trim하고 빈 문자열이면 null을 반환합니다.
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
