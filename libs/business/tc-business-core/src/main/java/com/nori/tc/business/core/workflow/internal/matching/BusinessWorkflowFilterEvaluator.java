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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * workflow_filter(JSON Rule)를 새 canonical 계약 기준으로 평가하는 컴포넌트입니다.
 *
 * <p>동작 원칙:</p>
 * <p>1) filter가 비어 있으면 true를 반환합니다.</p>
 * <p>2) 루트는 반드시 {@code and}/{@code or} 그룹 노드여야 합니다.</p>
 * <p>3) 조건 노드는 {@code from/path/comparison/expected/transforms}만 허용합니다.</p>
 * <p>4) transform 변환 실패 시 이전 값을 유지하고 warn 로그를 남깁니다.</p>
 */
@Component
public class BusinessWorkflowFilterEvaluator {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowFilterEvaluator.class);

    private static final Set<String> GROUP_KEYS = Set.of("and", "or");
    private static final Set<String> CONDITION_KEYS = Set.of("from", "path", "comparison", "expected", "transforms");

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
        return evaluateNode(parsedFilter.rootNode(), context, entry);
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
            final FilterGroupNode rootNode = parseRootNode(root);
            return ParsedFilterState.success(new ParsedFilter(rootNode));
        } catch (Exception ex) {
            log.error("workflow_filter parsing failed. filter={}", filter, ex);
            final String message = ex.getClass().getSimpleName() + ": " + normalizeExceptionMessage(ex.getMessage());
            return ParsedFilterState.failure(message);
        }
    }

    /**
     * 루트 그룹 노드를 파싱합니다.
     */
    private FilterGroupNode parseRootNode(final JsonNode root) {
        if (root == null || root.isNull() || !root.isObject()) {
            throw new IllegalArgumentException("workflow_filter root must be JSON object");
        }
        return parseGroupNode(root, true);
    }

    /**
     * 그룹/조건 노드를 재귀 파싱합니다.
     */
    private FilterNode parseNode(final JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            throw new IllegalArgumentException("workflow_filter node must be JSON object");
        }

        final Set<String> fieldNames = fieldNames(node);
        if (fieldNames.contains("and") || fieldNames.contains("or")) {
            return parseGroupNode(node, false);
        }
        return parseConditionNode(node);
    }

    /**
     * and/or 그룹 노드를 파싱합니다.
     */
    private FilterGroupNode parseGroupNode(final JsonNode node, final boolean root) {
        final Set<String> fieldNames = fieldNames(node);
        final boolean hasAnd = fieldNames.contains("and");
        final boolean hasOr = fieldNames.contains("or");

        if (!hasAnd && !hasOr) {
            if (root) {
                throw new IllegalArgumentException("workflow_filter root must be group node");
            }
            throw new IllegalArgumentException("group node must contain exactly one of and/or");
        }
        if (hasAnd && hasOr) {
            throw new IllegalArgumentException("group node cannot contain both and/or");
        }
        if (fieldNames.size() != 1) {
            throw new IllegalArgumentException("group node allows only and/or key");
        }

        final String groupKey = hasAnd ? "and" : "or";
        final JsonNode childrenNode = node.path(groupKey);
        if (!childrenNode.isArray()) {
            throw new IllegalArgumentException(groupKey + " must be array");
        }
        if (childrenNode.isEmpty()) {
            throw new IllegalArgumentException(groupKey + " group must not be empty");
        }

        final List<FilterNode> children = new ArrayList<>();
        for (JsonNode childNode : childrenNode) {
            children.add(parseNode(childNode));
        }
        return new FilterGroupNode(GroupType.fromKey(groupKey), List.copyOf(children));
    }

    /**
     * 조건 노드를 파싱합니다.
     */
    private FilterConditionNode parseConditionNode(final JsonNode node) {
        validateAllowedKeys(node, CONDITION_KEYS, "workflow_filter condition");

        final FilterLookupSourceType lookupSourceType = FilterLookupSourceType.fromText(
                requiredText(node, "from", "workflow_filter condition.from is required")
        );
        final String path = parseRelativePath(
                requiredText(node, "path", "workflow_filter condition.path is required")
        );
        final ComparisonType comparisonType = ComparisonType.fromText(
                requiredText(node, "comparison", "workflow_filter condition.comparison is required")
        );
        final JsonNode expectedNode = requiredNode(node, "expected", "workflow_filter condition.expected is required");
        if (expectedNode.isObject()) {
            throw new IllegalArgumentException("workflow_filter condition.expected must not be object");
        }
        final Object expected = jsonValue(expectedNode);
        final List<TransformSpec> transforms = parseTransforms(node.path("transforms"));
        return new FilterConditionNode(lookupSourceType, path, comparisonType, expected, transforms);
    }

    /**
     * transforms 체인을 파싱합니다.
     */
    private List<TransformSpec> parseTransforms(final JsonNode transformsNode) {
        if (transformsNode == null || transformsNode.isNull() || transformsNode.isMissingNode()) {
            return List.of();
        }
        if (!transformsNode.isArray()) {
            throw new IllegalArgumentException("workflow_filter transforms must be array");
        }

        final List<TransformSpec> transforms = new ArrayList<>();
        for (JsonNode child : transformsNode) {
            transforms.add(parseTransform(child));
        }
        return List.copyOf(transforms);
    }

    /**
     * 단일 transform spec을 파싱합니다.
     */
    private TransformSpec parseTransform(final JsonNode transformNode) {
        if (transformNode == null || transformNode.isNull() || transformNode.isMissingNode()) {
            throw new IllegalArgumentException("workflow_filter transform must not be null");
        }

        if (transformNode.isTextual()) {
            final String text = transformNode.asText();
            if (normalize(text) == null) {
                throw new IllegalArgumentException("workflow_filter transform text is blank");
            }
            return TransformSpec.fromCompactText(text);
        }

        if (!transformNode.isObject()) {
            throw new IllegalArgumentException("workflow_filter transform must be string or object");
        }

        final String name = requiredText(transformNode, "name", "workflow_filter transform.name is required");
        final JsonNode argsNode = transformNode.path("args");
        final List<Object> args = new ArrayList<>();
        if (!argsNode.isMissingNode()) {
            if (!argsNode.isArray()) {
                throw new IllegalArgumentException("workflow_filter transform.args must be array");
            }
            for (JsonNode argNode : argsNode) {
                args.add(jsonValue(argNode));
            }
        }
        return new TransformSpec(name.trim().toLowerCase(Locale.ROOT), List.copyOf(args));
    }

    /**
     * 재귀 AST를 실제 값으로 평가합니다.
     */
    private boolean evaluateNode(
            final FilterNode node,
            final BusinessWorkflowFilterContext context,
            final WorkflowRuntimeEntry entry
    ) {
        if (node instanceof FilterGroupNode groupNode) {
            return evaluateGroupNode(groupNode, context, entry);
        }
        if (node instanceof FilterConditionNode conditionNode) {
            return evaluateConditionNode(conditionNode, context, entry);
        }
        throw new IllegalStateException("Unsupported workflow filter node: " + node.getClass().getName());
    }

    /**
     * 그룹 노드를 평가합니다.
     */
    private boolean evaluateGroupNode(
            final FilterGroupNode groupNode,
            final BusinessWorkflowFilterContext context,
            final WorkflowRuntimeEntry entry
    ) {
        if (groupNode.groupType() == GroupType.AND) {
            for (FilterNode child : groupNode.children()) {
                if (!evaluateNode(child, context, entry)) {
                    return false;
                }
            }
            return true;
        }

        for (FilterNode child : groupNode.children()) {
            if (evaluateNode(child, context, entry)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 조건 노드를 평가합니다.
     */
    private boolean evaluateConditionNode(
            final FilterConditionNode conditionNode,
            final BusinessWorkflowFilterContext context,
            final WorkflowRuntimeEntry entry
    ) {
        Object leftValue = resolvePayloadValue(conditionNode.lookupSourceType(), conditionNode.path(), context);
        for (TransformSpec transform : conditionNode.transforms()) {
            leftValue = applyTransformWithPolicy(leftValue, transform, conditionNode, context, entry);
        }

        final boolean result = conditionNode.comparisonType().evaluate(leftValue, conditionNode.expected());
        if (!result && log.isDebugEnabled()) {
            log.debug("Workflow filter condition rejected. workflowKey={}, actionName={}, from={}, path={}, comparison={}",
                    entry.workflowKey(),
                    entry.actionName(),
                    conditionNode.lookupSourceType(),
                    conditionNode.path(),
                    conditionNode.comparisonType());
        }
        return result;
    }

    /**
     * 변환 적용 시 예외 정책을 처리합니다.
     *
     * <p>변환 실패 시 warn 로그를 남기고, 이전 값을 유지해 평가를 계속 진행합니다.</p>
     */
    private Object applyTransformWithPolicy(
            final Object previousValue,
            final TransformSpec transform,
            final FilterConditionNode conditionNode,
            final BusinessWorkflowFilterContext context,
            final WorkflowRuntimeEntry entry
    ) {
        try {
            return applyTransform(previousValue, transform);
        } catch (Exception ex) {
            log.warn("workflow_filter transform failed and previous value is preserved. eqpId={}, messageName={}, workflowKey={}, from={}, path={}, comparison={}, transform={}",
                    context.record().eqpId(),
                    context.record().messageName(),
                    entry.workflowKey(),
                    conditionNode.lookupSourceType(),
                    conditionNode.path(),
                    conditionNode.comparisonType(),
                    transform.name(),
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
     * payload 루트에서 data/metadata 블록을 선택한 뒤 상대 경로를 조회합니다.
     */
    @SuppressWarnings("unchecked")
    private static Object resolvePayloadValue(
            final FilterLookupSourceType lookupSourceType,
            final String path,
            final BusinessWorkflowFilterContext context
    ) {
        final Object block = context.messageVariables().get(lookupSourceType.rootKey());
        if (!(block instanceof Map<?, ?> map)) {
            return null;
        }
        return lookupPath((Map<String, Object>) map, path);
    }

    /**
     * dot-path를 사용해 중첩 맵 값을 조회합니다.
     */
    @SuppressWarnings("unchecked")
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
            current = ((Map<String, Object>) map).get(part);
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
     * JsonNode에서 필수 문자열을 읽습니다.
     */
    private static String requiredText(final JsonNode root, final String key, final String errorMessage) {
        final String value = textOrNull(root.path(key));
        if (value == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value;
    }

    /**
     * JsonNode에서 필수 노드를 읽습니다.
     */
    private static JsonNode requiredNode(final JsonNode root, final String key, final String errorMessage) {
        final JsonNode node = root.path(key);
        if (node == null || node.isMissingNode()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return node;
    }

    /**
     * 절대 경로를 차단하고 상대 경로만 허용합니다.
     */
    private static String parseRelativePath(final String rawPath) {
        final String path = normalize(rawPath);
        if (path == null) {
            throw new IllegalArgumentException("workflow_filter condition.path is required");
        }
        if (path.startsWith("data.") || path.startsWith("metadata.")) {
            throw new IllegalArgumentException("absolute path is not allowed. path=" + path);
        }
        return path;
    }

    /**
     * 허용되지 않은 키 존재 여부를 검증합니다.
     */
    private static void validateAllowedKeys(
            final JsonNode node,
            final Set<String> allowedKeys,
            final String subject
    ) {
        final Set<String> actualKeys = fieldNames(node);
        if (!allowedKeys.containsAll(actualKeys)) {
            final Set<String> invalidKeys = new LinkedHashSet<>(actualKeys);
            invalidKeys.removeAll(allowedKeys);
            throw new IllegalArgumentException(subject + " contains unsupported keys: " + invalidKeys);
        }
    }

    /**
     * JsonNode 객체의 key 집합을 추출합니다.
     */
    private static Set<String> fieldNames(final JsonNode node) {
        final Map<String, Boolean> names = new LinkedHashMap<>();
        final var iterator = node.properties().iterator();
        while (iterator.hasNext()) {
            names.put(iterator.next().getKey(), Boolean.TRUE);
        }
        return Set.copyOf(names.keySet());
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
            final Map<String, Object> mapped = new LinkedHashMap<>();
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
    private record ParsedFilter(FilterGroupNode rootNode) {
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
     * 재귀 필터 AST 공통 계약입니다.
     */
    private interface FilterNode {
    }

    /**
     * 그룹 노드 모델입니다.
     */
    private record FilterGroupNode(
            GroupType groupType,
            List<FilterNode> children
    ) implements FilterNode {
    }

    /**
     * 조건 노드 모델입니다.
     */
    private record FilterConditionNode(
            FilterLookupSourceType lookupSourceType,
            String path,
            ComparisonType comparisonType,
            Object expected,
            List<TransformSpec> transforms
    ) implements FilterNode {
    }

    /**
     * 그룹 타입입니다.
     */
    private enum GroupType {
        AND,
        OR;

        private static GroupType fromKey(final String key) {
            return switch (key) {
                case "and" -> AND;
                case "or" -> OR;
                default -> throw new IllegalArgumentException("invalid group key: " + key);
            };
        }
    }

    /**
     * workflow_filter 공개 계약에서 허용하는 조회 소스입니다.
     */
    private enum FilterLookupSourceType {
        DATA("data"),
        METADATA("metadata");

        private final String rootKey;

        FilterLookupSourceType(final String rootKey) {
            this.rootKey = rootKey;
        }

        private String rootKey() {
            return rootKey;
        }

        private static FilterLookupSourceType fromText(final String text) {
            final String normalized = normalize(text);
            if (normalized == null) {
                throw new IllegalArgumentException("workflow_filter condition.from is required");
            }
            return switch (normalized.toLowerCase(Locale.ROOT)) {
                case "data" -> DATA;
                case "metadata" -> METADATA;
                default -> throw new IllegalArgumentException("invalid from value: " + normalized);
            };
        }
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
            final String normalized = normalize(compactText);
            if (normalized == null) {
                throw new IllegalArgumentException("transform text is blank");
            }

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
                args.add(numeric != null ? numeric : unquoted);
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
     * 지원 비교 연산입니다.
     */
    private enum ComparisonType {
        EQUALS,
        NOT_EQUALS,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
        CONTAINS,
        IN;

        /**
         * 문자열을 비교 연산으로 변환합니다.
         */
        private static ComparisonType fromText(final String text) {
            final String normalized = normalize(text);
            if (normalized == null) {
                throw new IllegalArgumentException("workflow_filter condition.comparison is required");
            }
            return switch (normalized.toLowerCase(Locale.ROOT)) {
                case "equals" -> EQUALS;
                case "not_equals" -> NOT_EQUALS;
                case "greater_than" -> GREATER_THAN;
                case "greater_than_or_equal" -> GREATER_THAN_OR_EQUAL;
                case "less_than" -> LESS_THAN;
                case "less_than_or_equal" -> LESS_THAN_OR_EQUAL;
                case "contains" -> CONTAINS;
                case "in" -> IN;
                default -> throw new IllegalArgumentException("unsupported comparison: " + normalized);
            };
        }

        /**
         * 좌변/우변 값을 현재 연산자로 평가합니다.
         */
        private boolean evaluate(final Object leftValue, final Object rightValue) {
            if (leftValue == null) {
                return false;
            }
            return switch (this) {
                case EQUALS -> Objects.equals(normalizeComparable(leftValue), normalizeComparable(rightValue));
                case NOT_EQUALS -> !Objects.equals(normalizeComparable(leftValue), normalizeComparable(rightValue));
                case GREATER_THAN -> compare(leftValue, rightValue) > 0;
                case GREATER_THAN_OR_EQUAL -> compare(leftValue, rightValue) >= 0;
                case LESS_THAN -> compare(leftValue, rightValue) < 0;
                case LESS_THAN_OR_EQUAL -> compare(leftValue, rightValue) <= 0;
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
