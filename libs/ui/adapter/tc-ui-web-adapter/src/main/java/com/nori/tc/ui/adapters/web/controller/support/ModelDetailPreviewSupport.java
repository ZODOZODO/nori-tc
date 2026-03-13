package com.nori.tc.ui.adapters.web.controller.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.db.domain.model.TcModelWorkflow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Model 상세 화면의 workflow/filter/data index 축약 표시 문자열을 생성합니다.
 *
 * <p>UI가 raw JSON/XML을 그대로 table cell에 노출하지 않아도 되도록
 * 첫 번째 핵심 조건/필드 기준 preview를 제공합니다.</p>
 */
public final class ModelDetailPreviewSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> ACTION_DATA_INDEX_RESERVED_KEYS = List.of("mdf", "message", "messageName", "fields");

    private ModelDetailPreviewSupport() {
    }

    /**
     * workflow row의 preview 셀 값을 생성합니다.
     *
     * @param workflow workflow 원본 row
     * @return 컬럼 순서에 맞는 preview 값 목록
     */
    public static List<String> buildWorkflowPreviewValues(final TcModelWorkflow workflow) {
        final List<String> previewValues = new ArrayList<>(List.of(
                nullToEmpty(workflow.workflowName()),
                nullToEmpty(workflow.messageName()),
                nullToEmpty(workflow.eventId()),
                nullToEmpty(workflow.transactionId()),
                summarizeWorkflowFilter(workflow.workflowFilter()),
                nullToEmpty(workflow.actionName()),
                summarizeActionDataIndex(workflow.actionDataIndex())
        ));
        return List.copyOf(previewValues);
    }

    /**
     * workflow_filter에서 첫 번째 조건 기준 preview 문자열을 생성합니다.
     */
    public static String summarizeWorkflowFilter(final String workflowFilter) {
        final String normalized = normalizeFirstLine(workflowFilter);
        if (normalized.isEmpty()) {
            return "";
        }

        try {
            final JsonNode root = OBJECT_MAPPER.readTree(workflowFilter);
            final JsonNode firstRow = resolveFirstFilterRow(root);
            if (firstRow == null || firstRow.isMissingNode() || firstRow.isNull()) {
                return normalized;
            }

            final JsonNode expressionNode = resolveFilterExpressionNode(firstRow);
            final String variableName = textOrNull(expressionNode.path("var").path("name"));
            if (isBlank(variableName)) {
                return normalized;
            }

            final String source = normalizeSource(textOrNull(expressionNode.path("var").path("source")));
            final String operator = normalizeOperator(textOrNull(firstRow.path("op")), textOrNull(firstRow.path("operator")));
            final String rightValue = stringifyJsonValue(firstRow.path("right"));
            final String transforms = joinFilterTransforms(expressionNode.path("xform"));

            final StringBuilder summary = new StringBuilder();
            summary.append(variableName.trim()).append('[').append(source).append(']');
            if (!transforms.isEmpty()) {
                summary.append(" | ").append(transforms);
            }
            summary.append(' ').append(operator);
            if (!rightValue.isEmpty()) {
                summary.append(' ').append(rightValue);
            }
            return summary.toString();
        } catch (Exception ignored) {
            return normalized;
        }
    }

    /**
     * action_data_index에서 첫 번째 필드 기준 preview 문자열을 생성합니다.
     */
    public static String summarizeActionDataIndex(final String actionDataIndex) {
        final String normalized = normalizeFirstLine(actionDataIndex);
        if (normalized.isEmpty()) {
            return "";
        }

        try {
            final JsonNode root = OBJECT_MAPPER.readTree(actionDataIndex);
            final String messageName = firstNonBlank(
                    textOrNull(root.path("mdf")),
                    textOrNull(root.path("messageName")),
                    textOrNull(root.path("message"))
            );

            final Map.Entry<String, JsonNode> firstField = resolveFirstActionDataIndexField(root);
            if (firstField == null) {
                return isBlank(messageName) ? normalized : messageName;
            }

            final StringBuilder summary = new StringBuilder();
            if (!isBlank(messageName)) {
                summary.append(messageName.trim()).append(" / ");
            }
            summary.append(firstField.getKey()).append(' ');
            summary.append(buildActionDataIndexFieldSummary(firstField.getValue()));
            return summary.toString().trim();
        } catch (Exception ignored) {
            return normalized;
        }
    }

    private static JsonNode resolveFirstFilterRow(final JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }

        final JsonNode rowsNode = firstArrayNode(root, "rows", "conditions");
        if (rowsNode != null && rowsNode.isArray() && !rowsNode.isEmpty()) {
            return rowsNode.get(0);
        }
        return root;
    }

    private static JsonNode resolveFilterExpressionNode(final JsonNode rowNode) {
        final JsonNode leftNode = rowNode.path("left");
        if (!leftNode.isMissingNode() && !leftNode.isNull()) {
            return leftNode;
        }
        return rowNode.path("expr");
    }

    private static String joinFilterTransforms(final JsonNode xformNode) {
        if (xformNode == null || xformNode.isNull() || xformNode.isMissingNode()) {
            return "";
        }

        final List<String> transforms = new ArrayList<>();
        if (xformNode.isArray()) {
            for (JsonNode child : xformNode) {
                final String transform = normalizeFirstLine(child == null || child.isNull() ? "" : child.toString());
                final String normalizedTransform = stripJsonQuotes(transform);
                if (!normalizedTransform.isEmpty()) {
                    transforms.add(normalizedTransform);
                }
            }
            return String.join(" | ", transforms);
        }

        final String transform = stripJsonQuotes(normalizeFirstLine(xformNode.toString()));
        return transform;
    }

    private static String buildActionDataIndexFieldSummary(final JsonNode fieldNode) {
        if (fieldNode == null || fieldNode.isNull()) {
            return "<- (empty)";
        }
        if (fieldNode.isTextual()) {
            return "<- " + fieldNode.asText() + "[AUTO]";
        }
        if (fieldNode.isNumber() || fieldNode.isBoolean()) {
            return "= " + fieldNode.asText();
        }
        if (!fieldNode.isObject()) {
            return "<- " + normalizeFirstLine(fieldNode.toString());
        }

        final String fixed = textOrNull(fieldNode.path("fixed"));
        if (!isBlank(fixed)) {
            return "= " + fixed.trim();
        }

        final String variablePath = textOrNull(fieldNode.path("var"));
        final String source = normalizeSource(textOrNull(fieldNode.path("source")));
        final String transforms = joinFilterTransforms(fieldNode.path("xform"));
        final boolean required = fieldNode.path("required").isMissingNode() || fieldNode.path("required").asBoolean(true);

        final StringBuilder summary = new StringBuilder();
        summary.append("<- ");
        if (!isBlank(variablePath)) {
            summary.append(variablePath.trim()).append('[').append(source).append(']');
        } else {
            summary.append("(empty)");
        }
        if (!transforms.isEmpty()) {
            summary.append(" | ").append(transforms);
        }
        if (!required) {
            summary.append(" | optional");
        }
        return summary.toString();
    }

    private static Map.Entry<String, JsonNode> resolveFirstActionDataIndexField(final JsonNode root) {
        if (root == null || root.isNull() || !root.isObject()) {
            return null;
        }

        final JsonNode fieldsNode = root.path("fields");
        if (fieldsNode.isObject()) {
            final Iterator<Map.Entry<String, JsonNode>> iterator = fieldsNode.fields();
            if (iterator.hasNext()) {
                return iterator.next();
            }
            return null;
        }

        final Iterator<Map.Entry<String, JsonNode>> iterator = root.fields();
        while (iterator.hasNext()) {
            final Map.Entry<String, JsonNode> entry = iterator.next();
            if (!ACTION_DATA_INDEX_RESERVED_KEYS.contains(entry.getKey())) {
                return entry;
            }
        }
        return null;
    }

    private static JsonNode firstArrayNode(final JsonNode root, final String... fieldNames) {
        if (root == null || fieldNames == null) {
            return null;
        }

        for (String fieldName : fieldNames) {
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            final JsonNode candidate = root.path(fieldName);
            if (!candidate.isMissingNode() && candidate.isArray()) {
                return candidate;
            }
        }
        return null;
    }

    private static String stringifyJsonValue(final JsonNode valueNode) {
        if (valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
            return "";
        }
        if (valueNode.isTextual()) {
            return valueNode.asText();
        }
        if (valueNode.isNumber() || valueNode.isBoolean()) {
            return valueNode.asText();
        }
        if (valueNode.isArray()) {
            final List<String> values = new ArrayList<>();
            for (JsonNode child : valueNode) {
                values.add(stringifyJsonValue(child));
            }
            return "[" + String.join(", ", values) + "]";
        }
        if (valueNode.isObject()) {
            final Map<String, String> flattened = new LinkedHashMap<>();
            final Iterator<Map.Entry<String, JsonNode>> iterator = valueNode.fields();
            while (iterator.hasNext()) {
                final Map.Entry<String, JsonNode> entry = iterator.next();
                flattened.put(entry.getKey(), stringifyJsonValue(entry.getValue()));
            }
            final List<String> pairs = new ArrayList<>();
            for (Map.Entry<String, String> entry : flattened.entrySet()) {
                pairs.add(entry.getKey() + "=" + entry.getValue());
            }
            return "{" + String.join(", ", pairs) + "}";
        }
        return normalizeFirstLine(valueNode.toString());
    }

    private static String normalizeFirstLine(final String value) {
        if (isBlank(value)) {
            return "";
        }

        final String[] lines = value.split("\\R");
        for (String line : lines) {
            final String normalizedLine = collapseWhitespace(line);
            if (!normalizedLine.isEmpty()) {
                return normalizedLine;
            }
        }
        return collapseWhitespace(value);
    }

    private static String normalizeSource(final String rawSource) {
        if (isBlank(rawSource)) {
            return "AUTO";
        }
        return rawSource.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeOperator(final String op, final String fallbackOperator) {
        final String resolved = firstNonBlank(op, fallbackOperator);
        if (isBlank(resolved)) {
            return "eq";
        }
        return resolved.trim().toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(final String value) {
        return value == null ? "" : value;
    }

    private static String textOrNull(final JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        final String text = node.asText();
        return isBlank(text) ? null : text;
    }

    private static String firstNonBlank(final String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String stripJsonQuotes(final String value) {
        if (isBlank(value)) {
            return "";
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String collapseWhitespace(final String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
