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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@code action_data_index}를 새 canonical 계약 기준으로 해석하는 컴포넌트입니다.
 *
 * <p>외부 계약은 아래 규칙만 허용합니다.</p>
 * <p>1) 루트 키: {@code mdfTemplateName}, {@code fields}</p>
 * <p>2) 필드 shorthand: 문자열 경로({@code from=data} 기본값)</p>
 * <p>3) 필드 객체: {@code from}, {@code path}, {@code transforms}</p>
 */
@Component
public class BusinessActionDataIndexHybridResolver {

    private static final Logger log = LoggerFactory.getLogger(BusinessActionDataIndexHybridResolver.class);

    private static final Set<String> ROOT_KEYS = Set.of("mdfTemplateName", "fields");
    private static final Set<String> FIELD_KEYS = Set.of("from", "path", "transforms");

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

        try {
            final JsonNode root = objectMapper.readTree(normalized);
            if (!root.isObject()) {
                throw new IllegalArgumentException("action_data_index root must be JSON object");
            }

            validateAllowedKeys(root, ROOT_KEYS, "action_data_index root");

            final String mdfTemplateName = requiredText(root, "mdfTemplateName", "mdfTemplateName is required");
            final JsonNode fieldsNode = requiredNode(root, "fields", "fields is required");
            if (!fieldsNode.isObject()) {
                throw new IllegalArgumentException("fields must be JSON object");
            }

            final Map<String, ValueSpec> fieldSpecs = parseFieldSpecObject(fieldsNode);
            return new ParsedActionDataIndex(mdfTemplateName, Map.copyOf(fieldSpecs));
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
            log.warn("action_data_index field value is missing and replaced with empty string. field={}, workflowKey={}",
                    fieldName,
                    context.workflowEntry().workflowKey());
            return "";
        }

        Object current = value;
        for (TransformSpec transform : valueSpec.transforms()) {
            final Object before = current;
            current = applyTransformWithPolicy(current, transform, fieldName, context);
            if (log.isTraceEnabled()) {
                log.trace("action_data_index transform applied. workflowKey={}, field={}, transform={}, before={}, after={}",
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
            log.warn("action_data_index field resolved to null/blank and replaced with empty string. field={}, workflowKey={}",
                    fieldName,
                    context.workflowEntry().workflowKey());
            return "";
        }

        return asText;
    }

    /**
     * fields 객체를 필드 정의로 파싱합니다.
     */
    private static Map<String, ValueSpec> parseFieldSpecObject(final JsonNode fieldsNode) {
        final Map<String, ValueSpec> target = new LinkedHashMap<>();
        final var iterator = fieldsNode.properties().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<String, JsonNode> entry = iterator.next();
            final String fieldName = normalize(entry.getKey());
            if (fieldName == null) {
                throw new IllegalArgumentException("field name must not be blank");
            }
            target.put(fieldName, parseValueSpec(entry.getValue(), fieldName));
        }
        return Map.copyOf(target);
    }

    /**
     * 단일 field spec을 파싱합니다.
     */
    private static ValueSpec parseValueSpec(final JsonNode node, final String fieldName) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("field spec must not be null. field=" + fieldName);
        }

        if (node.isTextual()) {
            final String path = parseRelativePath(node.asText(), "field path is required. field=" + fieldName);
            return ValueSpec.payloadPath(path, LookupSourceType.DATA, List.of());
        }

        if (!node.isObject()) {
            throw new IllegalArgumentException("field spec must be string or object. field=" + fieldName);
        }

        validateAllowedKeys(node, FIELD_KEYS, "field spec. field=" + fieldName);

        final LookupSourceType lookupSourceType = LookupSourceType.fromActionFieldFrom(
                requiredText(node, "from", "from is required. field=" + fieldName)
        );
        final String path = parseRelativePath(
                requiredText(node, "path", "path is required. field=" + fieldName),
                "path is required. field=" + fieldName
        );
        final List<TransformSpec> transforms = parseTransforms(
                node.path("transforms"),
                "transforms must be array. field=" + fieldName
        );
        return ValueSpec.payloadPath(path, lookupSourceType, transforms);
    }

    /**
     * transforms 체인을 파싱합니다.
     */
    private static List<TransformSpec> parseTransforms(final JsonNode transformsNode, final String invalidMessage) {
        if (transformsNode == null || transformsNode.isNull() || transformsNode.isMissingNode()) {
            return List.of();
        }
        if (!transformsNode.isArray()) {
            throw new IllegalArgumentException(invalidMessage);
        }

        final List<TransformSpec> transforms = new ArrayList<>();
        for (JsonNode child : transformsNode) {
            transforms.add(parseTransformSpec(child));
        }
        return List.copyOf(transforms);
    }

    /**
     * 단일 transform spec을 파싱합니다.
     */
    private static TransformSpec parseTransformSpec(final JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            throw new IllegalArgumentException("transform spec must not be null");
        }
        if (node.isTextual()) {
            return TransformSpec.fromCompactText(node.asText());
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("transform spec must be string or object");
        }

        final String name = requiredText(node, "name", "transform name is required");
        final JsonNode argsNode = node.path("args");
        final List<Object> args = new ArrayList<>();
        if (!argsNode.isMissingNode()) {
            if (!argsNode.isArray()) {
                throw new IllegalArgumentException("transform args must be array");
            }
            for (JsonNode arg : argsNode) {
                args.add(jsonValue(arg));
            }
        }
        return new TransformSpec(name.toLowerCase(Locale.ROOT), List.copyOf(args));
    }

    /**
     * lookup source/path 기준으로 변수 값을 조회합니다.
     */
    private static Object resolveVariable(final ValueSpec valueSpec, final BusinessWorkflowActionContext context) {
        final String path = normalize(valueSpec.variablePath());
        if (path == null) {
            return null;
        }

        return switch (valueSpec.lookupSourceType()) {
            case DATA -> lookupPayloadBlock(context.messageVariables(), "data", path);
            case METADATA -> lookupPayloadBlock(context.messageVariables(), "metadata", path);
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
            log.warn("action_data_index transform failed and previous value is preserved. workflowKey={}, field={}, transform={}",
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
     * payload 루트에서 data/metadata 블록을 선택한 뒤 상대 경로를 조회합니다.
     */
    @SuppressWarnings("unchecked")
    private static Object lookupPayloadBlock(
            final Map<String, Object> payloadRoot,
            final String blockName,
            final String path
    ) {
        if (payloadRoot == null || blockName == null) {
            return null;
        }
        final Object block = payloadRoot.get(blockName);
        if (!(block instanceof Map<?, ?> map)) {
            return null;
        }
        return lookupPath((Map<String, Object>) map, path);
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
     * path를 상대 경로 규칙에 맞게 검증합니다.
     */
    private static String parseRelativePath(final String rawPath, final String blankMessage) {
        final String path = normalize(rawPath);
        if (path == null) {
            throw new IllegalArgumentException(blankMessage);
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
        final Set<String> names = new LinkedHashSet<>();
        final var iterator = node.properties().iterator();
        while (iterator.hasNext()) {
            names.add(iterator.next().getKey());
        }
        return Set.copyOf(names);
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
            String mdfTemplateName,
            Map<String, ValueSpec> fields
    ) {

        public ParsedActionDataIndex {
            mdfTemplateName = normalize(mdfTemplateName);
            fields = fields == null ? Map.of() : Map.copyOf(fields);
        }

        public static ParsedActionDataIndex empty() {
            return new ParsedActionDataIndex(null, Map.of());
        }

        public boolean isEmpty() {
            return mdfTemplateName == null && fields.isEmpty();
        }
    }

    /**
     * 단일 필드 value spec 모델입니다.
     */
    public record ValueSpec(
            String variablePath,
            LookupSourceType lookupSourceType,
            List<TransformSpec> transforms,
            String fixedValue,
            boolean required
    ) {

        public ValueSpec {
            variablePath = normalize(variablePath);
            lookupSourceType = lookupSourceType == null ? LookupSourceType.AUTO : lookupSourceType;
            transforms = transforms == null ? List.of() : List.copyOf(transforms);
            fixedValue = normalize(fixedValue);
        }

        public static ValueSpec payloadPath(
                final String variablePath,
                final LookupSourceType lookupSourceType,
                final List<TransformSpec> transforms
        ) {
            return new ValueSpec(variablePath, lookupSourceType, transforms, null, false);
        }
    }

    /**
     * 필드 값 조회 소스 타입입니다.
     *
     * <p>{@code DATA}/{@code METADATA}는 새 공개 계약, 나머지는 기존 MDF field fallback 전용입니다.</p>
     */
    public enum LookupSourceType {
        DATA,
        METADATA,
        MSG,
        CTX,
        AUTO;

        /**
         * 새 action_data_index 공개 계약의 from 값을 변환합니다.
         */
        public static LookupSourceType fromActionFieldFrom(final String text) {
            final String normalized = normalize(text);
            if (normalized == null) {
                throw new IllegalArgumentException("from is required");
            }
            return switch (normalized.toLowerCase(Locale.ROOT)) {
                case "data" -> DATA;
                case "metadata" -> METADATA;
                default -> throw new IllegalArgumentException("invalid from value: " + normalized);
            };
        }

        /**
         * 기존 MDF field 정의의 sourceType을 내부 조회 소스로 변환합니다.
         */
        public static LookupSourceType fromMdfSourceType(final MdfSourceType sourceType) {
            final MdfSourceType resolved = sourceType == null ? MdfSourceType.AUTO : sourceType;
            return switch (resolved) {
                case MSG -> MSG;
                case CTX -> CTX;
                case AUTO -> AUTO;
            };
        }
    }

    /**
     * transforms 스펙 모델입니다.
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
