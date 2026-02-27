package com.nori.tc.business.core.workflow.internal.support;

import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 워크플로우 액션에서 공통으로 사용하는 command 관련 값 추출 유틸리티입니다.
 *
 * <p>메시지 변수/컨텍스트에서 우선순위 규칙에 따라 값을 조회하고,
 * 조회 실패 시 기본값으로 보완하는 공통 로직을 제공합니다.</p>
 */
public final class BusinessWorkflowCommandSupport {

    /**
     * 유틸리티 클래스의 인스턴스 생성을 방지합니다.
     */
    private BusinessWorkflowCommandSupport() {
    }

    /**
     * command eventType을 우선순위에 따라 결정합니다.
     *
     * <p>우선순위:
     * 1) messageVariables.command.eventType
     * 2) messageVariables.commandEventType
     * 3) messageVariables.metadata.eventType
     * 4) inbound messageName
     * 5) fallbackEventType
     * </p>
     *
     * @param context 액션 실행 컨텍스트
     * @param fallbackEventType 모든 경로 조회 실패 시 사용할 기본 eventType
     * @return 최종 eventType
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
     * traceId를 우선순위에 따라 조회합니다.
     *
     * <p>우선순위:
     * 1) messageVariables.metadata.traceId
     * 2) messageVariables.traceId
     * 3) trace://{payloadRef} 형식의 fallback 값
     * </p>
     *
     * @param context 액션 실행 컨텍스트
     * @return 최종 traceId
     */
    public static String resolveTraceId(final BusinessWorkflowActionContext context) {
        Objects.requireNonNull(context, "context is null");
        final String fromRecord = normalize(context.record().traceId());
        if (fromRecord != null) {
            return fromRecord;
        }
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
     * correlationId를 우선순위에 따라 조회합니다.
     *
     * <p>우선순위:
     * 1) messageVariables.metadata.correlationId
     * 2) messageVariables.correlationId
     * 3) messageVariables.lotId
     * </p>
     *
     * @param context 액션 실행 컨텍스트
     * @return 조회된 correlationId, 없으면 null
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
     * transactionId를 우선순위에 따라 조회합니다.
     *
     * <p>우선순위:
     * 1) messageVariables.data.transactionId
     * 2) messageVariables.transactionId
     * </p>
     *
     * @param context 액션 실행 컨텍스트
     * @return 조회된 transactionId, 없으면 null
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
     * 원문 메시지(rawMessage)를 우선순위에 따라 조회합니다.
     *
     * <p>우선순위:
     * 1) messageVariables.data.rawMessage
     * 2) messageVariables.rawMessage
     * 3) inbound payload
     * </p>
     *
     * @param context 액션 실행 컨텍스트
     * @return 조회된 rawMessage, 없으면 null
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
     * command 발행 시 공통으로 사용되는 payload 맵을 생성합니다.
     *
     * <p>포함 항목:
     * eqpId, messageName, payloadRef, workflowKey, workflowName, actionName,
     * actionDataIndex, messageVariables, contextVariables
     * </p>
     *
     * @param context 액션 실행 컨텍스트
     * @return 불변(immutable) command payload 맵
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
     * 여러 경로(path)를 순서대로 조회하여 첫 번째 유효 문자열을 반환합니다.
     *
     * @param root 조회 대상 루트 맵
     * @param paths dot notation 경로 배열
     * @return 첫 번째 유효 문자열, 없으면 null
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
     * dot notation 경로를 따라 맵 내부 값을 탐색합니다.
     *
     * @param root 루트 맵
     * @param path 점 표기 경로
     * @return 조회된 객체, 경로 중간에 값이 없으면 null
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
     * 임의의 객체를 문자열로 변환하고 공백을 정규화합니다.
     *
     * @param value 변환 대상 값
     * @return 정규화된 문자열, 변환 불가 또는 빈 문자열이면 null
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
     * 문자열 양끝 공백을 제거하고 빈 문자열을 null로 정규화합니다.
     *
     * @param value 원본 문자열
     * @return 정규화된 문자열
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

