package com.nori.tc.business.core.workflow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Workflow 액션에서 Kafka 명령 발행 데이터를 추출할 때 사용하는 공통 지원 클래스입니다.
 *
 * <p>역할:
 * 1) message/context 변수에서 경로 기반 값 조회
 * 2) eventType/traceId/correlationId/rawMessage 같은 공통 필드 해석
 * 3) 기본 command payload 맵 구성</p>
 */
public final class BusinessWorkflowCommandSupport {

    /**
     * 유틸리티 클래스 인스턴스 생성을 막습니다.
     */
    private BusinessWorkflowCommandSupport() {
    }

    /**
     * Command eventType을 해석합니다.
     *
     * <p>우선순위:
     * 1) messageVariables.command.eventType
     * 2) messageVariables.commandEventType
     * 3) messageVariables.metadata.eventType
     * 4) inbound messageName
     * 5) fallbackEventType</p>
     *
     * @param context 액션 컨텍스트
     * @param fallbackEventType 최종 대체값
     * @return 해석된 eventType
     */
    public static String resolveCommandEventType(
            final BusinessWorkflowActionContext context,
            final String fallbackEventType
    ) {
        Objects.requireNonNull(context, "context is null");
        final String resolved = firstText(
                context.messageVariables(),
                "command.eventType",
                "commandEventType",
                "metadata.eventType"
        );
        if (resolved != null) {
            return resolved;
        }

        final String fromRecord = normalize(context.record().messageName());
        if (fromRecord != null) {
            return fromRecord;
        }

        final String fallback = normalize(fallbackEventType);
        if (fallback == null) {
            throw new IllegalArgumentException("fallbackEventType is required");
        }
        return fallback;
    }

    /**
     * traceId를 해석합니다.
     *
     * <p>우선순위:
     * 1) messageVariables.metadata.traceId
     * 2) messageVariables.traceId
     * 3) payloadRef 기반 fallback</p>
     *
     * @param context 액션 컨텍스트
     * @return 해석된 traceId
     */
    public static String resolveTraceId(final BusinessWorkflowActionContext context) {
        Objects.requireNonNull(context, "context is null");
        final String fromVariables = firstText(
                context.messageVariables(),
                "metadata.traceId",
                "traceId"
        );
        if (fromVariables != null) {
            return fromVariables;
        }
        return "trace://" + context.record().payloadRef();
    }

    /**
     * correlationId를 해석합니다.
     *
     * <p>우선순위:
     * 1) messageVariables.metadata.correlationId
     * 2) messageVariables.correlationId
     * 3) messageVariables.lotId</p>
     *
     * @param context 액션 컨텍스트
     * @return 해석된 correlationId
     */
    public static String resolveCorrelationId(final BusinessWorkflowActionContext context) {
        Objects.requireNonNull(context, "context is null");
        return firstText(
                context.messageVariables(),
                "metadata.correlationId",
                "correlationId",
                "lotId"
        );
    }

    /**
     * transactionId를 해석합니다.
     *
     * @param context 액션 컨텍스트
     * @return 해석된 transactionId(없으면 null)
     */
    public static String resolveTransactionId(final BusinessWorkflowActionContext context) {
        Objects.requireNonNull(context, "context is null");
        return firstText(
                context.messageVariables(),
                "data.transactionId",
                "transactionId"
        );
    }

    /**
     * rawMessage를 해석합니다.
     *
     * <p>우선순위:
     * 1) messageVariables.data.rawMessage
     * 2) messageVariables.rawMessage
     * 3) inbound payload 원문</p>
     *
     * @param context 액션 컨텍스트
     * @return 해석된 rawMessage
     */
    public static String resolveRawMessage(final BusinessWorkflowActionContext context) {
        Objects.requireNonNull(context, "context is null");
        final String fromVariables = firstText(
                context.messageVariables(),
                "data.rawMessage",
                "rawMessage"
        );
        if (fromVariables != null) {
            return fromVariables;
        }
        return normalize(context.record().payload());
    }

    /**
     * command payload 공통 맵을 구성합니다.
     *
     * <p>설계 의도:
     * 하위 어댑터에서 최소한의 정보만으로도 라우팅/추적이 가능하도록
     * workflow/ingress 핵심 컨텍스트를 함께 전달합니다.</p>
     *
     * @param context 액션 컨텍스트
     * @return 불변 command payload 맵
     */
    public static Map<String, Object> buildCommandPayload(final BusinessWorkflowActionContext context) {
        Objects.requireNonNull(context, "context is null");
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eqpId", context.record().eqpId());
        payload.put("messageName", context.record().messageName());
        payload.put("payloadRef", context.record().payloadRef());
        payload.put("workflowKey", context.workflowEntry().workflowKey());
        payload.put("workflowName", context.workflowEntry().workflowName());
        payload.put("actionName", context.workflowEntry().actionName());
        payload.put("actionDataIndex", context.workflowEntry().actionDataIndex());
        payload.put("messageVariables", context.messageVariables());
        payload.put("contextVariables", context.contextVariables());
        return Map.copyOf(payload);
    }

    /**
     * 변수 맵에서 첫 번째 유효 문자열 값을 찾습니다.
     *
     * @param root 루트 맵
     * @param paths 조회 경로 목록
     * @return 첫 번째 유효 문자열(없으면 null)
     */
    public static String firstText(final Map<String, Object> root, final String... paths) {
        Objects.requireNonNull(paths, "paths is null");
        for (String path : paths) {
            final String normalizedPath = normalize(path);
            if (normalizedPath == null) {
                continue;
            }
            final Object value = lookup(root, normalizedPath);
            final String text = toText(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    /**
     * 점(.) 경로로 중첩 맵 값을 조회합니다.
     *
     * @param root 루트 맵
     * @param path 점 경로
     * @return 조회값(없으면 null)
     */
    @SuppressWarnings("unchecked")
    private static Object lookup(final Map<String, Object> root, final String path) {
        if (root == null || root.isEmpty()) {
            return null;
        }
        Object cursor = root;
        for (String segment : path.split("\\.")) {
            if (!(cursor instanceof Map<?, ?> current)) {
                return null;
            }
            cursor = ((Map<String, Object>) current).get(segment);
            if (cursor == null) {
                return null;
            }
        }
        return cursor;
    }

    /**
     * 값을 문자열로 안전하게 변환합니다.
     *
     * @param value 원본 값
     * @return 정규화된 문자열(없으면 null)
     */
    private static String toText(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return normalize(text);
        }
        return normalize(String.valueOf(value));
    }

    /**
     * 문자열을 trim하고 빈 문자열은 null로 변환합니다.
     *
     * @param value 원본 문자열
     * @return 정규화된 문자열(없으면 null)
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
