package com.nori.tc.business.core.workflow.internal.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfSourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * {@code action_data_index}를 해석하는 하이브리드 해석기입니다.
 *
 * <p>
 * 지원 규칙:
 * 1) 문자열: MDF 메시지명 선택자(예: TOOL_CONDITION_REQUEST_EQP)
 * 2) 객체: {@code mdf/messageName} + {@code fields} 매핑
 * 3) 필드 식: 문자열 경로 또는 객체식(var/source/xform/fixed/required)
 * </p>
 */
@Component
public class BusinessActionDataIndexHybridResolver {

    private static final Logger log = LoggerFactory.getLogger(BusinessActionDataIndexHybridResolver.class);

    private final ObjectMapper objectMapper;

    /**
     * JSON 파서를 주입받습니다.
     */
    public BusinessActionDataIndexHybridResolver(final ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is null");
    }

    /**
     * action_data_index 원문을 파싱합니다.
     */
    public ParsedActionDataIndex parse(final String actionDataIndex) {
        final String normalized = normalize(actionDataIndex);
        if (normalized == null) {
            return ParsedActionDataIndex.empty();
        }

        // JSON 객체가 아니면 MDF 메시지명 선택자로 해석합니다.
        if (!(normalized.startsWith("{") && normalized.endsWith("}"))) {
            return new ParsedActionDataIndex(normalized, Map.of());
        }

        try {
            final JsonNode root = objectMapper.readTree(normalized);
            if (!root.isObject()) {
                throw new IllegalArgumentException("action_data_index root must be JSON object");
            }

            final String messageName = firstText(root, "mdf", "messageName", "message");
            final JsonNode fieldsNode = root.path("fields");

            final Map<String, ValueSpec> fieldSpecs = new LinkedHashMap<>();
            if (fieldsNode.isObject()) {
                parseFieldSpecObject(fieldsNode, fieldSpecs);
            } else {
                // fields가 없으면 예약 키를 제외한 루트 key를 필드 정의로 해석합니다.
                parseRootAsFieldSpec(root, fieldSpecs);
            }

            return new ParsedActionDataIndex(messageName, Map.copyOf(fieldSpecs));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid action_data_index format", ex);
        }
    }

    /**
     * 단일 필드 식을 평가해 문자열 값을 반환합니다.
     */
    public String resolveFieldValue(
            final String fieldName,
            final ValueSpec valueSpec,
            final BusinessWorkflowActionContext context
    ) {
        Objects.requireNonNull(valueSpec, "valueSpec is null");
        Objects.requireNonNull(context, "context is null");

        Object value;
        if (valueSpec.fixedValue() != null) {
            value = valueSpec.fixedValue();
        } else {
            value = resolveVariable(valueSpec, context);
        }

        if (value == null) {
            if (valueSpec.required()) {
                throw new IllegalArgumentException("Required field value is missing. field=" + fieldName);
            }
            log.warn("Optional field value is missing and replaced with empty string. field={}, workflowKey={}",
                    fieldName,
                    context.workflowEntry().workflowKey());
            return "";
        }

        Object current = value;
        for (TransformSpec transform : valueSpec.transforms()) {
            final Object before = current;
            current = applyTransformWithPolicy(current, transform, fieldName, context);
            if (log.isTraceEnabled()) {
                log.trace("action_data_index xform applied. workflowKey={}, field={}, transform={}, before={}, after={}",
                        context.workflowEntry().workflowKey(),
                        fieldName,
                        transform.name(),
                        before,
                        current);
            }
        }

        final String asText = toText(current);
        if (asText == null) {
            if (valueSpec.required()) {
                throw new IllegalArgumentException("Required field resolved to null/blank. field=" + fieldName);
            }
            log.warn("Optional field resolved to null/blank and replaced with empty string. field={}, workflowKey={}",
                    fieldName,
                    context.workflowEntry().workflowKey());
            return "";
        }

        return asText;
    }

    /**
     * 루트 객체의 key/value를 필드 정의로 파싱합니다.
     */
    private static void parseRootAsFieldSpec(JsonNode root, Map<String, ValueSpec> target) {
        final List<String> reservedKeys = List.of("mdf", "message", "messageName", "fields");
        final var iterator = root.properties().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<String, JsonNode> entry = iterator.next();
            if (reservedKeys.contains(entry.getKey())) {
                continue;
            }
            target.put(entry.getKey(), parseValueSpec(entry.getValue()));
        }
    }

    /**
     * fields 객체를 필드 정의로 파싱합니다.
     */
    private static void parseFieldSpecObject(JsonNode fieldsNode, Map<String, ValueSpec> target) {
        final var iterator = fieldsNode.properties().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<String, JsonNode> entry = iterator.next();
            target.put(entry.getKey(), parseValueSpec(entry.getValue()));
        }
    }

    /**
     * 단일 value spec을 파싱합니다.
     */
    private static ValueSpec parseValueSpec(JsonNode node) {
        if (node == null || node.isNull()) {
            return ValueSpec.requiredPath(null);
        }
        if (node.isTextual()) {
            return ValueSpec.requiredPath(node.asText());
        }
        if (node.isNumber() || node.isBoolean()) {
            return ValueSpec.fixed(String.valueOf(node.asText()));
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("Unsupported value spec node type: " + node.getNodeType());
        }

        final String fixed = textOrNull(node.path("fixed"));
        final String var = textOrNull(node.path("var"));
        final MdfSourceType source = MdfSourceType.fromTextOrDefault(textOrNull(node.path("source")), MdfSourceType.AUTO);
        final boolean required = node.path("required").isMissingNode() || node.path("required").asBoolean(true);

        final List<TransformSpec> transforms = parseTransforms(node.path("xform"));

        if (fixed != null) {
            return new ValueSpec(null, source, transforms, fixed, required);
        }
        return new ValueSpec(var, source, transforms, null, required);
    }

    /**
     * xform 체인을 파싱합니다.
     */
    private static List<TransformSpec> parseTransforms(JsonNode xformNode) {
        if (xformNode == null || xformNode.isNull() || xformNode.isMissingNode()) {
            return List.of();
        }

        final List<TransformSpec> transforms = new ArrayList<>();
        if (xformNode.isArray()) {
            for (JsonNode child : xformNode) {
                final TransformSpec spec = parseTransformSpec(child);
                if (spec != null) {
                    transforms.add(spec);
                }
            }
            return List.copyOf(transforms);
        }

        final TransformSpec single = parseTransformSpec(xformNode);
        if (single != null) {
            transforms.add(single);
        }
        return List.copyOf(transforms);
    }

    /**
     * 단일 transform spec을 파싱합니다.
     */
    private static TransformSpec parseTransformSpec(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return TransformSpec.fromCompactText(node.asText());
        }
        if (!node.isObject()) {
            return null;
        }

        final String name = textOrNull(node.path("name"));
        if (name == null) {
            return null;
        }

        final List<Object> args = new ArrayList<>();
        final JsonNode argsNode = node.path("args");
        if (argsNode.isArray()) {
            for (JsonNode arg : argsNode) {
                args.add(jsonValue(arg));
            }
        }
        return new TransformSpec(name.toLowerCase(Locale.ROOT), List.copyOf(args));
    }

    /**
     * source/path 기준으로 변수 값을 조회합니다.
     */
    private static Object resolveVariable(ValueSpec valueSpec, BusinessWorkflowActionContext context) {
        final String path = normalize(valueSpec.variablePath());
        if (path == null) {
            return null;
        }

        return switch (valueSpec.sourceType()) {
            case MSG -> lookupPath(context.messageVariables(), path);
            case CTX -> lookupPath(context.contextVariables(), path);
            case AUTO -> {
                final Object fromMessage = lookupPath(context.messageVariables(), path);
                if (fromMessage != null) {
                    yield fromMessage;
                }
                yield lookupPath(context.contextVariables(), path);
            }
        };
    }

    /**
     * transform을 적용하고 실패 시 이전 값을 유지합니다.
     */
    private Object applyTransformWithPolicy(
            final Object value,
            final TransformSpec transform,
            final String fieldName,
            final BusinessWorkflowActionContext context
    ) {
        try {
            return applyTransform(value, transform);
        } catch (Exception ex) {
            log.warn("action_data_index xform failed and previous value is preserved. workflowKey={}, field={}, transform={}",
                    context.workflowEntry().workflowKey(),
                    fieldName,
                    transform.name(),
                    ex);
            return value;
        }
    }

    /**
     * transform 이름에 맞는 변환 함수를 수행합니다.
     */
    private static Object applyTransform(final Object value, final TransformSpec transform) {
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
    private static Object transformSplit(final Object value, final List<Object> args) {
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
    private static Object transformSubstring(final Object value, final List<Object> args) {
        if (value == null || args.isEmpty()) {
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
    private static Object transformAddSub(final Object value, final List<Object> args, final boolean add) {
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
     * dot-path로 중첩 Map 값을 조회합니다.
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
     * JsonNode를 Java 값으로 변환합니다.
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
            final Map<String, Object> values = new LinkedHashMap<>();
            final var fields = node.properties().iterator();
            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> field = fields.next();
                values.put(field.getKey(), jsonValue(field.getValue()));
            }
            return Map.copyOf(values);
        }
        return node.asText();
    }

    /**
     * 숫자 변환 실패 시 null을 반환합니다.
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
     * args[index]를 int로 변환합니다.
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
     * JsonNode를 null-safe 문자열로 변환합니다.
     */
    private static String textOrNull(final JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return normalize(node.asText(null));
    }

    /**
     * 우선순위 key 목록에서 첫 문자열 값을 반환합니다.
     */
    private static String firstText(final JsonNode root, final String... keys) {
        for (String key : keys) {
            final String value = textOrNull(root.path(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 값을 문자열로 정규화합니다.
     */
    private static String toText(final Object value) {
        if (value == null) {
            return null;
        }
        return normalize(String.valueOf(value));
    }

    /**
     * 문자열 trim 후 공백이면 null을 반환합니다.
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
     * 파싱된 action_data_index 모델입니다.
     */
    public record ParsedActionDataIndex(
            String messageName,
            Map<String, ValueSpec> fieldSpecs
    ) {

        public ParsedActionDataIndex {
            messageName = normalize(messageName);
            fieldSpecs = fieldSpecs == null ? Map.of() : Map.copyOf(fieldSpecs);
        }

        public static ParsedActionDataIndex empty() {
            return new ParsedActionDataIndex(null, Map.of());
        }

        public boolean isEmpty() {
            return messageName == null && fieldSpecs.isEmpty();
        }
    }

    /**
     * 단일 필드 value spec 모델입니다.
     */
    public record ValueSpec(
            String variablePath,
            MdfSourceType sourceType,
            List<TransformSpec> transforms,
            String fixedValue,
            boolean required
    ) {

        public ValueSpec {
            variablePath = normalize(variablePath);
            sourceType = sourceType == null ? MdfSourceType.AUTO : sourceType;
            transforms = transforms == null ? List.of() : List.copyOf(transforms);
            fixedValue = normalize(fixedValue);
        }

        public static ValueSpec requiredPath(final String variablePath) {
            return new ValueSpec(variablePath, MdfSourceType.AUTO, List.of(), null, true);
        }

        public static ValueSpec fixed(final String fixedValue) {
            return new ValueSpec(null, MdfSourceType.AUTO, List.of(), fixedValue, true);
        }
    }

    /**
     * xform 스펙 모델입니다.
     */
    public record TransformSpec(
            String name,
            List<Object> args
    ) {

        public TransformSpec {
            name = normalize(name);
            if (name == null) {
                throw new IllegalArgumentException("transform name is required");
            }
            name = name.toLowerCase(Locale.ROOT);
            args = args == null ? List.of() : List.copyOf(args);
        }

        /**
         * compact 문자열을 transform spec으로 파싱합니다.
         */
        public static TransformSpec fromCompactText(final String compactText) {
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
                final String stripped = stripQuotes(token.trim());
                final BigDecimal numeric = toBigDecimal(stripped);
                args.add(numeric == null ? stripped : numeric);
            }
            return new TransformSpec(name, List.copyOf(args));
        }

        /**
         * 양끝의 동일한 따옴표를 제거합니다.
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
}
