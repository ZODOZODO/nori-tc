package com.nori.tc.business.domain.modelcache;

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
 * MDF는 메시지 구조와 field 목록만 선언합니다.
 * MDF는 어떤 workflow에서 사용될지 알지 못하며, 알 필요도 없습니다.
 * 값 바인딩은 workflow의 action_data_index가 담당합니다.
 * </p>
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
     * MDF 정의가 비어 있는지 여부를 반환합니다.
     */
    public boolean isEmpty() {
        return messagesByName.isEmpty();
    }

    /**
     * MDF 메시지 정의 모델입니다.
     *
     * <p>
     * {@code template}은 output이 {@link MdfOutputType#RAW_MESSAGE}인 경우에만 필요합니다.
     * {@link MdfOutputType#KAFKA}인 경우 template 없이 field 목록만 선언합니다.
     * </p>
     */
    public record MdfMessageDefinition(
            String name,
            MdfTargetType targetType,
            MdfOutputType outputType,
            String template,
            List<MdfFieldDefinition> fields
    ) {

        /**
         * 필수 값을 검증하고 문자열/리스트를 정규화합니다.
         *
         * <p>template은 output이 RAW_MESSAGE인 경우에만 필수입니다.</p>
         */
        public MdfMessageDefinition {
            name = normalizeRequired("name", name);
            Objects.requireNonNull(targetType, "targetType is null");
            Objects.requireNonNull(outputType, "outputType is null");
            // RAW_MESSAGE는 template 필수, KAFKA는 field 목록으로 직렬화하므로 template 불필요
            template = normalizeNullable(template);
            if (outputType == MdfOutputType.RAW_MESSAGE && template == null) {
                throw new IllegalArgumentException("template is required when outputType is RAW_MESSAGE. message=" + name);
            }
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
     *
     * <p>
     * {@code name}은 template의 {@code {EQPID}} 자리에서 치환 위치를 식별하는 이름입니다.
     * {@code variablePath}는 action_data_index에서 값을 가져올 lookup key 이름입니다.
     * </p>
     */
    public record MdfFieldDefinition(
            String name,
            String variablePath,
            boolean required
    ) {

        /**
         * 필드 정의를 정규화합니다.
         */
        public MdfFieldDefinition {
            name = normalizeRequired("name", name);
            variablePath = normalizeRequired("variablePath", variablePath);
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
     * MDF 메시지 출력 포맷입니다.
     *
     * <ul>
     *   <li>{@link #RAW_MESSAGE}: template 문자열 직렬화 → EQP 장비 전송 (예: {@code CMD=... EQPID=xxx})</li>
     *   <li>{@link #KAFKA}: Kafka 메시지 포맷 직렬화 → MES 전송 (metadata + data 블록)</li>
     * </ul>
     */
    public enum MdfOutputType {
        RAW_MESSAGE,
        KAFKA;

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
