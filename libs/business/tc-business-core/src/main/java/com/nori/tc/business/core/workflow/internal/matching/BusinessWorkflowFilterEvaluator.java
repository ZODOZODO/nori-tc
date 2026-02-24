package com.nori.tc.business.core.workflow.internal.matching;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterContext;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterEvaluationException;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * workflow_filter(JSON Rule)를 평가하는 컴포넌트입니다.
 *
 * <p>동작 원칙:</p>
 * <p>1) filter가 비어 있으면 true를 반환합니다.</p>
 * <p>2) rows/conditions는 AND로 평가합니다.</p>
 * <p>3) xform 변환 실패 시 이전 값을 유지하고 warn 로그를 남깁니다.</p>
 * <p>4) 변수 source는 MSG/CTX/AUTO(MSG 우선)를 지원합니다.</p>
 */
@Component
public class BusinessWorkflowFilterEvaluator {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowFilterEvaluator.class);

    /**
     * 동일한 filter 문자열의 재파싱 비용을 줄이기 위한 캐시입니다.
     *
     * <p>파싱 실패 상태도 함께 캐시하여 반복적인 실패 파싱을 방지합니다.</p>
     */
    private final Map<String, ParsedFilterState> parsedFilterCache = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    /**
     * 생성자에서 JSON 파서를 주입받습니다.
     *
     * @param objectMapper Jackson ObjectMapper
     */
    public BusinessWorkflowFilterEvaluator(final ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is null");
    }

    /**
     * 워크플로우 엔트리의 filter를 평가합니다.
     *
     * @param entry 워크플로우 엔트리
     * @param context 필터 평가 컨텍스트
     * @return 필터 통과 여부
     */
    public boolean evaluate(final WorkflowRuntimeEntry entry, final BusinessWorkflowFilterContext context) {
        Objects.requireNonNull(entry, "entry is null");
        Objects.requireNonNull(context, "context is null");

        final String filter = normalize(entry.workflowFilter());
        if (filter == null) {
            return true;
        }

        final ParsedFilter parsedFilter = resolveParsedFilter(filter, entry);
        if (parsedFilter.rows().isEmpty()) {
            return true;
        }

        for (FilterRow row : parsedFilter.rows()) {
            final boolean rowResult = evaluateRow(row, context);
            if (!rowResult) {
                if (log.isDebugEnabled()) {
                    log.debug("Workflow filter row rejected. workflowKey={}, actionName={}, operator={}",
                            entry.workflowKey(),
                            entry.actionName(),
                            row.operator());
                }
                return false;
            }
        }
        return true;
    }

    /**
     * 필터 문자열을 파싱 결과로 변환하거나 캐시된 결과를 반환합니다.
     *
     * @param filter workflow_filter 원문
     * @param entry 워크플로우 엔트리
     * @return 파싱 완료된 필터 모델
     */
    private ParsedFilter resolveParsedFilter(final String filter, final WorkflowRuntimeEntry entry) {
        final ParsedFilterState state = parsedFilterCache.computeIfAbsent(filter, this::parseFilterState);
        if (state.isSuccess()) {
            return state.parsedFilter();
        }

        throw new BusinessWorkflowFilterEvaluationException(
                "workflow_filter parsing failed. workflowKey=" + entry.workflowKey() + ", reason=" + state.errorMessage()
        );
    }

    /**
     * 필터 문자열을 실제 파싱해 캐시 상태 객체로 변환합니다.
     *
     * @param filter workflow_filter 원문
     * @return 파싱 성공/실패 상태
     */
    private ParsedFilterState parseFilterState(final String filter) {
        try {
            final JsonNode root = objectMapper.readTree(filter);
            final List<FilterRow> rows = parseRows(root);
            return ParsedFilterState.success(new ParsedFilter(rows));
        } catch (Exception ex) {
            log.warn("workflow_filter parsing failed. filter={}", filter, ex);
            final String message = ex.getClass().getSimpleName() + ": " + normalizeExceptionMessage(ex.getMessage());
            return ParsedFilterState.failure(message);
        }
    }

    /**
     * JSON 루트에서 row 목록을 파싱합니다.
     *
     * <p>지원 형태:</p>
     * <p>1) {"rows":[...]}</p>
     * <p>2) {"conditions":[...]}</p>
     * <p>3) 단일 row 객체</p>
     *
     * @param root 필터 JSON 루트
     * @return 파싱된 row 목록
     */
    private List<FilterRow> parseRows(final JsonNode root) {
        final List<FilterRow> rows = new ArrayList<>();

        if (root == null || root.isNull()) {
            return rows;
        }

        final JsonNode rowsNode = firstArrayNode(root, "rows", "conditions");
        if (rowsNode != null) {
            for (JsonNode rowNode : rowsNode) {
                final FilterRow row = parseRow(rowNode);
                if (row != null) {
                    rows.add(row);
                }
            }
            return rows;
        }

        final FilterRow singleRow = parseRow(root);
        if (singleRow != null) {
            rows.add(singleRow);
        }
        return rows;
    }

    /**
     * 단일 row 노드를 파싱합니다.
     *
     * @param node row JSON 노드
     * @return 파싱 성공 시 row, 불완전한 노드면 null
     */
    private FilterRow parseRow(final JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        final JsonNode leftNode = node.path("left");
        final JsonNode expressionNode = leftNode.isMissingNode() ? node.path("expr") : leftNode;
        if (expressionNode == null || expressionNode.isMissingNode() || expressionNode.isNull()) {
            return null;
        }

        final LeftExpression leftExpression = parseLeftExpression(expressionNode);
        if (leftExpression == null) {
            return null;
        }

        final String operatorRaw = textOrNull(node.path("op"));
        final String fallbackOperatorRaw = textOrNull(node.path("operator"));
        final OperatorType operatorType = OperatorType.fromText(
                normalize(operatorRaw) != null ? operatorRaw : fallbackOperatorRaw
        );

        final JsonNode rightNode = node.path("right");
        final Object rightValue = jsonValue(rightNode);
        return new FilterRow(leftExpression, operatorType, rightValue);
    }

    /**
     * 좌변 표현식을 파싱합니다.
     *
     * @param expressionNode left/expr 노드
     * @return 파싱된 좌변 표현식
     */
    private LeftExpression parseLeftExpression(final JsonNode expressionNode) {
        final JsonNode varNode = expressionNode.path("var");
        if (varNode == null || varNode.isMissingNode() || varNode.isNull()) {
            return null;
        }

        final String varName = textOrNull(varNode.path("name"));
        final SourceType sourceType = SourceType.fromText(textOrNull(varNode.path("source")));
        if (normalize(varName) == null) {
            return null;
        }

        final List<TransformSpec> transforms = new ArrayList<>();
        final JsonNode xformNode = expressionNode.path("xform");
        if (xformNode != null && xformNode.isArray()) {
            for (JsonNode transformNode : xformNode) {
                final TransformSpec spec = parseTransform(transformNode);
                if (spec != null) {
                    transforms.add(spec);
                }
            }
        }

        return new LeftExpression(
                new VariableRef(varName.trim(), sourceType),
                List.copyOf(transforms)
        );
    }

    /**
     * 변환 스펙을 파싱합니다.
     *
     * @param transformNode xform 노드
     * @return 파싱된 변환 스펙
     */
    private TransformSpec parseTransform(final JsonNode transformNode) {
        if (transformNode == null || transformNode.isNull()) {
            return null;
        }

        if (transformNode.isTextual()) {
            final String text = transformNode.asText();
            if (normalize(text) == null) {
                return null;
            }
            return TransformSpec.fromCompactText(text);
        }

        final String name = textOrNull(transformNode.path("name"));
        if (normalize(name) == null) {
            return null;
        }

        final List<Object> args = new ArrayList<>();
        final JsonNode argsNode = transformNode.path("args");
        if (argsNode != null && argsNode.isArray()) {
            for (JsonNode argNode : argsNode) {
                args.add(jsonValue(argNode));
            }
        }
        return new TransformSpec(name.trim().toLowerCase(Locale.ROOT), List.copyOf(args));
    }

    /**
     * 단일 row를 실제 값으로 평가합니다.
     *
     * @param row 필터 row
     * @param context 평가 컨텍스트
     * @return row 평가 결과
     */
    private boolean evaluateRow(final FilterRow row, final BusinessWorkflowFilterContext context) {
        Object leftValue = resolveVariable(row.leftExpression().variableRef(), context);
        for (TransformSpec transform : row.leftExpression().transforms()) {
            leftValue = applyTransformWithPolicy(leftValue, transform, row, context);
        }

        final Object rightValue = row.rightValue();
        return row.operator().evaluate(leftValue, rightValue);
    }

    /**
     * 변환 적용 시 예외 정책을 처리합니다.
     *
     * <p>변환 실패 시 warn 로그를 남기고, 이전 값을 유지해 평가를 계속 진행합니다.</p>
     */
    private Object applyTransformWithPolicy(
            final Object previousValue,
            final TransformSpec transform,
            final FilterRow row,
            final BusinessWorkflowFilterContext context
    ) {
        try {
            return applyTransform(previousValue, transform);
        } catch (Exception ex) {
            log.warn("workflow_filter xform failed and previous value is preserved. eqpId={}, messageName={}, transform={}, operator={}",
                    context.record().eqpId(),
                    context.record().messageName(),
                    transform.name(),
                    row.operator(),
                    ex);
            return previousValue;
        }
    }

    /**
     * 변환 스펙 이름에 맞는 변환 함수를 실행합니다.
     */
    private Object applyTransform(final Object value, final TransformSpec transform) {
        return switch (transform.name()) {
            case "split" -> transformSplit(value, transform.args());
            case "trim" -> value == null ? null : String.valueOf(value).trim();
            case "substring" -> transformSubstring(value, transform.args());
            case "toint" -> toBigDecimal(value) == null ? null : toBigDecimal(value).intValue();
            case "tolong" -> toBigDecimal(value) == null ? null : toBigDecimal(value).longValue();
            case "length" -> value == null ? null : String.valueOf(value).length();
            case "add" -> transformAddSub(value, transform.args(), true);
            case "sub" -> transformAddSub(value, transform.args(), false);
            case "lower" -> value == null ? null : String.valueOf(value).toLowerCase(Locale.ROOT);
            case "upper" -> value == null ? null : String.valueOf(value).toUpperCase(Locale.ROOT);
            default -> value;
        };
    }

    /**
     * split 변환을 수행합니다.
     */
    private Object transformSplit(final Object value, final List<Object> args) {
        if (value == null) {
            return null;
        }

        final String source = String.valueOf(value);
        final String delimiter = args.size() >= 1 ? String.valueOf(args.get(0)) : ",";
        final int index = toIntOrDefault(args, 1, 0);
        final String[] tokens = source.split(java.util.regex.Pattern.quote(delimiter), -1);
        if (index < 0 || index >= tokens.length) {
            return value;
        }
        return tokens[index];
    }

    /**
     * substring 변환을 수행합니다.
     */
    private Object transformSubstring(final Object value, final List<Object> args) {
        if (value == null) {
            return null;
        }
        if (args.isEmpty()) {
            return value;
        }

        final String source = String.valueOf(value);
        final int start = toIntOrDefault(args, 0, 0);
        final int end = toIntOrDefault(args, 1, source.length());
        final int safeStart = Math.max(0, Math.min(start, source.length()));
        final int safeEnd = Math.max(safeStart, Math.min(end, source.length()));
        return source.substring(safeStart, safeEnd);
    }

    /**
     * add/sub 변환을 수행합니다.
     */
    private Object transformAddSub(final Object value, final List<Object> args, final boolean add) {
        if (value == null || args.isEmpty()) {
            return value;
        }

        final BigDecimal left = toBigDecimal(value);
        final BigDecimal right = toBigDecimal(args.get(0));
        if (left == null || right == null) {
            return value;
        }
        return add ? left.add(right) : left.subtract(right);
    }

    /**
     * 변수 참조(source/name)를 실제 값으로 조회합니다.
     */
    private Object resolveVariable(final VariableRef variableRef, final BusinessWorkflowFilterContext context) {
        return switch (variableRef.sourceType()) {
            case MSG -> lookupPath(context.messageVariables(), variableRef.name());
            case CTX -> lookupPath(context.contextVariables(), variableRef.name());
            case AUTO -> {
                final Object fromMessage = lookupPath(context.messageVariables(), variableRef.name());
                if (fromMessage != null) {
                    yield fromMessage;
                }
                yield lookupPath(context.contextVariables(), variableRef.name());
            }
        };
    }

    /**
     * dot-path를 사용해 중첩 맵 값을 조회합니다.
     */
    private static Object lookupPath(final Map<String, Object> source, final String path) {
        if (source == null || path == null || path.isBlank()) {
            return null;
        }
        if (source.containsKey(path)) {
            return source.get(path);
        }

        Object current = source;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * args[index]를 정수로 파싱하고 실패 시 기본값을 반환합니다.
     */
    private static int toIntOrDefault(final List<Object> args, final int index, final int defaultValue) {
        if (args == null || index < 0 || index >= args.size()) {
            return defaultValue;
        }
        final BigDecimal number = toBigDecimal(args.get(index));
        if (number == null) {
            return defaultValue;
        }
        return number.intValue();
    }

    /**
     * 객체를 BigDecimal로 변환합니다.
     */
    private static BigDecimal toBigDecimal(final Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 복수 후보 필드 중 첫 번째 배열 노드를 반환합니다.
     */
    private static JsonNode firstArrayNode(final JsonNode root, final String... names) {
        for (String name : names) {
            final JsonNode node = root.path(name);
            if (node != null && node.isArray()) {
                return node;
            }
        }
        return null;
    }

    /**
     * JsonNode를 null-safe 텍스트로 변환합니다.
     */
    private static String textOrNull(final JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        final String text = node.asText(null);
        return normalize(text);
    }

    /**
     * JsonNode를 Java 값으로 재귀 변환합니다.
     */
    private static Object jsonValue(final JsonNode node) {
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
            final List<Object> values = new ArrayList<>();
            for (JsonNode child : node) {
                values.add(jsonValue(child));
            }
            return List.copyOf(values);
        }
        if (node.isObject()) {
            final Map<String, Object> mapped = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                mapped.put(field.getKey(), jsonValue(field.getValue()));
            }
            return Map.copyOf(mapped);
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

    /**
     * 예외 메시지를 로그 친화 형태로 정규화합니다.
     */
    private static String normalizeExceptionMessage(final String message) {
        final String normalized = normalize(message);
        return normalized == null ? "n/a" : normalized;
    }

    /**
     * 파싱 완료된 필터 모델입니다.
     */
    private record ParsedFilter(List<FilterRow> rows) {
        private ParsedFilter {
            rows = List.copyOf(rows == null ? List.of() : rows);
        }
    }

    /**
     * 필터 파싱 성공/실패 상태를 저장하는 캐시 모델입니다.
     */
    private record ParsedFilterState(
            ParsedFilter parsedFilter,
            String errorMessage
    ) {
        /**
         * 파싱 성공 상태를 생성합니다.
         */
        private static ParsedFilterState success(final ParsedFilter parsedFilter) {
            return new ParsedFilterState(parsedFilter, null);
        }

        /**
         * 파싱 실패 상태를 생성합니다.
         */
        private static ParsedFilterState failure(final String errorMessage) {
            return new ParsedFilterState(null, errorMessage);
        }

        /**
         * 성공 여부를 반환합니다.
         */
        private boolean isSuccess() {
            return parsedFilter != null;
        }
    }

    /**
     * 필터 row 모델입니다.
     */
    private record FilterRow(
            LeftExpression leftExpression,
            OperatorType operator,
            Object rightValue
    ) {
    }

    /**
     * 좌변 표현식 모델입니다.
     */
    private record LeftExpression(
            VariableRef variableRef,
            List<TransformSpec> transforms
    ) {
    }

    /**
     * 변수 참조 모델입니다.
     */
    private record VariableRef(
            String name,
            SourceType sourceType
    ) {
    }

    /**
     * 변환 스펙 모델입니다.
     */
    private record TransformSpec(
            String name,
            List<Object> args
    ) {
        /**
         * compact 문자열을 변환 스펙으로 파싱합니다.
         *
         * @param compactText 예: split(",",0)
         * @return 변환 스펙
         */
        private static TransformSpec fromCompactText(final String compactText) {
            final String normalized = compactText.trim();
            final int openIndex = normalized.indexOf('(');
            final int closeIndex = normalized.lastIndexOf(')');

            if (openIndex < 0 || closeIndex < openIndex) {
                return new TransformSpec(normalized.toLowerCase(Locale.ROOT), List.of());
            }

            final String name = normalized.substring(0, openIndex).trim().toLowerCase(Locale.ROOT);
            final String argsSection = normalized.substring(openIndex + 1, closeIndex).trim();
            if (argsSection.isEmpty()) {
                return new TransformSpec(name, List.of());
            }

            final List<Object> args = new ArrayList<>();
            for (String token : argsSection.split(",")) {
                final String trimmed = token.trim();
                final String unquoted = stripQuotes(trimmed);
                final BigDecimal numeric = toBigDecimal(unquoted);
                if (numeric != null) {
                    args.add(numeric);
                } else {
                    args.add(unquoted);
                }
            }
            return new TransformSpec(name, List.copyOf(args));
        }

        /**
         * 작은따옴표/큰따옴표로 감싼 문자열의 외곽 따옴표를 제거합니다.
         */
        private static String stripQuotes(final String value) {
            if (value == null || value.length() < 2) {
                return value;
            }
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }
    }

    /**
     * 변수 조회 소스 타입입니다.
     */
    private enum SourceType {
        MSG,
        CTX,
        AUTO;

        /**
         * 문자열을 SourceType으로 변환합니다.
         *
         * @param text source 문자열
         * @return 변환된 SourceType, 실패 시 AUTO
         */
        private static SourceType fromText(final String text) {
            final String normalized = normalize(text);
            if (normalized == null) {
                return SourceType.AUTO;
            }
            try {
                return SourceType.valueOf(normalized.toUpperCase(Locale.ROOT));
            } catch (Exception ex) {
                return SourceType.AUTO;
            }
        }
    }

    /**
     * 지원 연산자 타입입니다.
     */
    private enum OperatorType {
        EQ,
        NE,
        GT,
        GTE,
        LT,
        LTE,
        CONTAINS,
        IN;

        /**
         * 문자열을 연산자 타입으로 변환합니다.
         *
         * @param text 연산자 문자열
         * @return 변환된 연산자 타입
         */
        private static OperatorType fromText(final String text) {
            final String normalized = normalize(text);
            if (normalized == null) {
                return EQ;
            }
            return switch (normalized.toLowerCase(Locale.ROOT)) {
                case "eq", "==" -> EQ;
                case "ne", "!=", "<>" -> NE;
                case "gt", ">" -> GT;
                case "gte", ">=" -> GTE;
                case "lt", "<" -> LT;
                case "lte", "<=" -> LTE;
                case "contains" -> CONTAINS;
                case "in" -> IN;
                default -> EQ;
            };
        }

        /**
         * 좌변/우변 값을 현재 연산자로 평가합니다.
         */
        private boolean evaluate(final Object leftValue, final Object rightValue) {
            return switch (this) {
                case EQ -> Objects.equals(normalizeComparable(leftValue), normalizeComparable(rightValue));
                case NE -> !Objects.equals(normalizeComparable(leftValue), normalizeComparable(rightValue));
                case GT -> compare(leftValue, rightValue) > 0;
                case GTE -> compare(leftValue, rightValue) >= 0;
                case LT -> compare(leftValue, rightValue) < 0;
                case LTE -> compare(leftValue, rightValue) <= 0;
                case CONTAINS -> {
                    if (leftValue == null || rightValue == null) {
                        yield false;
                    }
                    yield String.valueOf(leftValue).contains(String.valueOf(rightValue));
                }
                case IN -> {
                    if (rightValue instanceof List<?> list) {
                        yield list.stream().anyMatch(item -> Objects.equals(
                                normalizeComparable(item),
                                normalizeComparable(leftValue)
                        ));
                    }
                    yield false;
                }
            };
        }

        /**
         * 숫자 우선 비교 후 문자열 비교를 수행합니다.
         */
        private static int compare(final Object leftValue, final Object rightValue) {
            final BigDecimal leftNumber = toBigDecimal(leftValue);
            final BigDecimal rightNumber = toBigDecimal(rightValue);
            if (leftNumber != null && rightNumber != null) {
                return leftNumber.compareTo(rightNumber);
            }

            final String leftText = leftValue == null ? null : String.valueOf(leftValue);
            final String rightText = rightValue == null ? null : String.valueOf(rightValue);
            if (leftText == null && rightText == null) {
                return 0;
            }
            if (leftText == null) {
                return -1;
            }
            if (rightText == null) {
                return 1;
            }
            return leftText.compareTo(rightText);
        }

        /**
         * 동등 비교를 위한 정규화 값을 반환합니다.
         */
        private static Object normalizeComparable(final Object value) {
            if (value instanceof BigDecimal decimal) {
                return decimal.stripTrailingZeros();
            }
            if (value instanceof Number number) {
                final BigDecimal decimal = toBigDecimal(number);
                return decimal == null ? value : decimal.stripTrailingZeros();
            }
            return value;
        }
    }
}
