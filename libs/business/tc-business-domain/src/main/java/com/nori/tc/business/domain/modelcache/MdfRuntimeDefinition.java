package com.nori.tc.business.domain.modelcache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MDF(XML)에서 파싱한 메시지 정의 집합입니다.
 *
 * <p>
 * 런타임에서는 modelVersionKey 단위로 이 정의를 보유하며,
 * 액션 실행기가 EQP/MES 발행 메시지를 조립할 때 사용합니다.
 * </p>
 */
public record MdfRuntimeDefinition(
        Map<String, MdfMessageDefinition> messagesByName
) {

    /**
     * 생성 시 메시지 정의를 불변 맵으로 정규화합니다.
     */
    public MdfRuntimeDefinition {
        final Map<String, MdfMessageDefinition> copied = new LinkedHashMap<>();
        if (messagesByName != null) {
            for (Map.Entry<String, MdfMessageDefinition> entry : messagesByName.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                copied.put(entry.getKey().trim(), entry.getValue());
            }
        }
        messagesByName = Map.copyOf(copied);
    }

    /**
     * 비어 있는 MDF 정의를 반환합니다.
     */
    public static MdfRuntimeDefinition empty() {
        return new MdfRuntimeDefinition(Map.of());
    }

    /**
     * 메시지 이름으로 정의를 조회합니다.
     */
    public Optional<MdfMessageDefinition> findMessage(String messageName) {
        if (messageName == null || messageName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(messagesByName.get(messageName.trim()));
    }

    /**
     * 액션명 + 타겟(EQP/MES) 기준으로 메시지 정의를 조회합니다.
     */
    public List<MdfMessageDefinition> findByActionAndTarget(String actionName, MdfTargetType targetType) {
        if (actionName == null || actionName.isBlank() || targetType == null) {
            return List.of();
        }

        final String normalizedAction = actionName.trim();
        final List<MdfMessageDefinition> matched = new ArrayList<>();
        for (MdfMessageDefinition message : messagesByName.values()) {
            if (message.targetType() == targetType && message.actionName().equals(normalizedAction)) {
                matched.add(message);
            }
        }
        return List.copyOf(matched);
    }

    /**
     * MDF 정의가 비어 있는지 여부를 반환합니다.
     */
    public boolean isEmpty() {
        return messagesByName.isEmpty();
    }

    /**
     * MDF 메시지 정의 모델입니다.
     */
    public record MdfMessageDefinition(
            String name,
            MdfTargetType targetType,
            MdfOutputType outputType,
            String actionName,
            String template,
            List<MdfFieldDefinition> fields
    ) {

        /**
         * 필수 값을 검증하고 문자열/리스트를 정규화합니다.
         */
        public MdfMessageDefinition {
            name = normalizeRequired("name", name);
            Objects.requireNonNull(targetType, "targetType is null");
            Objects.requireNonNull(outputType, "outputType is null");
            actionName = normalizeRequired("actionName", actionName);
            template = normalizeRequired("template", template);
            fields = fields == null ? List.of() : List.copyOf(fields);
        }

        /**
         * 필드 이름으로 필드 정의를 조회합니다.
         */
        public Optional<MdfFieldDefinition> findField(String fieldName) {
            if (fieldName == null || fieldName.isBlank()) {
                return Optional.empty();
            }
            final String normalized = fieldName.trim();
            for (MdfFieldDefinition field : fields) {
                if (field.name().equals(normalized)) {
                    return Optional.of(field);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * MDF 필드 정의 모델입니다.
     */
    public record MdfFieldDefinition(
            String name,
            String variablePath,
            MdfSourceType sourceType,
            List<String> xformChain,
            String fixedValue,
            boolean required
    ) {

        /**
         * 필드 정의를 정규화합니다.
         */
        public MdfFieldDefinition {
            name = normalizeRequired("name", name);
            variablePath = normalizeNullable(variablePath);
            sourceType = sourceType == null ? MdfSourceType.AUTO : sourceType;
            fixedValue = normalizeNullable(fixedValue);

            final List<String> normalizedXform = new ArrayList<>();
            if (xformChain != null) {
                for (String xform : xformChain) {
                    final String normalized = normalizeNullable(xform);
                    if (normalized != null) {
                        normalizedXform.add(normalized);
                    }
                }
            }
            xformChain = List.copyOf(normalizedXform);

            if (fixedValue == null && variablePath == null) {
                throw new IllegalArgumentException("Either fixedValue or variablePath must be provided. field=" + name);
            }
        }
    }

    /**
     * MDF 메시지 타겟입니다.
     */
    public enum MdfTargetType {
        EQP,
        MES;

        /**
         * 문자열을 타겟 타입으로 변환합니다.
         */
        public static Optional<MdfTargetType> fromText(String text) {
            final String normalized = normalizeNullable(text);
            if (normalized == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(MdfTargetType.valueOf(normalized.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }
    }

    /**
     * MDF 메시지 출력 위치입니다.
     */
    public enum MdfOutputType {
        RAW_MESSAGE,
        DATA;

        /**
         * 문자열을 출력 타입으로 변환합니다.
         */
        public static Optional<MdfOutputType> fromText(String text) {
            final String normalized = normalizeNullable(text);
            if (normalized == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(MdfOutputType.valueOf(normalized.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }
    }

    /**
     * MDF 값 조회 소스 타입입니다.
     */
    public enum MdfSourceType {
        AUTO,
        MSG,
        CTX;

        /**
         * 문자열을 소스 타입으로 변환합니다.
         */
        public static MdfSourceType fromTextOrDefault(String text, MdfSourceType defaultValue) {
            final String normalized = normalizeNullable(text);
            if (normalized == null) {
                return defaultValue;
            }
            try {
                return MdfSourceType.valueOf(normalized.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return defaultValue;
            }
        }
    }

    /**
     * 필수 문자열을 검증/정규화합니다.
     */
    private static String normalizeRequired(String field, String value) {
        final String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    /**
     * 문자열을 trim 후 공백이면 null을 반환합니다.
     */
    private static String normalizeNullable(String value) {
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
