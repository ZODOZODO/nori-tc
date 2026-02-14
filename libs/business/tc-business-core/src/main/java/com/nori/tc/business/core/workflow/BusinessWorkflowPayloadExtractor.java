package com.nori.tc.business.core.workflow;

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
 * inbound payload(JSON) 추출기입니다.
 *
 * <p>역할:</p>
 * <p>1) payload 문자열을 JSON 맵으로 역직렬화</p>
 * <p>2) SECS 매칭에 필요한 eventId/transactionId 후보 경로 추출</p>
 * <p>3) 필터 평가용 runtime context 변수 맵 생성</p>
 */
@Component
public class BusinessWorkflowPayloadExtractor {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowPayloadExtractor.class);

    private final ObjectMapper objectMapper;

    /**
     * 추출기 의존성을 주입받습니다.
     */
    public BusinessWorkflowPayloadExtractor(final ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is null");
    }

    /**
     * payload를 메시지 변수 맵으로 추출합니다.
     *
     * @param payload payload 문자열
     * @return 추출 결과 맵. 파싱 실패 시 빈 맵
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
     * payload에서 eventId를 추출합니다.
     *
     * @param payload payload 문자열
     * @return eventId(없으면 null)
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
     * payload에서 transactionId를 추출합니다.
     *
     * @param payload payload 문자열
     * @return transactionId(없으면 null)
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
     * 필터 평가용 context 변수 맵을 생성합니다.
     *
     * @param record inbound 레코드
     * @return context 변수 맵
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
            node.fields().forEachRemaining(field -> values.put(field.getKey(), toValue(field.getValue())));
            return Map.copyOf(values);
        }
        return node.asText();
    }

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


