package com.nori.tc.business.core.workflow.internal.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * workflow transform 함수의 공통 구현체입니다.
 *
 * <p>기존에 {@link BusinessActionDataIndexHybridResolver}와
 * {@code BusinessWorkflowFilterEvaluator}에 중복 구현되어 있던
 * {@code TransformSpec} record와 {@code applyTransform()} 메서드를 공통으로 추출합니다.</p>
 *
 * <p>재사용 대상:</p>
 * <ul>
 *   <li>{@link BusinessActionDataIndexHybridResolver} - action_data_index transforms</li>
 *   <li>{@code BusinessWorkflowFilterEvaluator} - workflow_filter transforms</li>
 *   <li>{@code CollectDcdataTcAction} - DCOP Item Calculation Rule 후처리</li>
 * </ul>
 *
 * <p>지원 transform 함수 목록:</p>
 * <ul>
 *   <li>{@code split(delimiter, index)}: 구분자로 분할 후 인덱스번째 토큰 반환</li>
 *   <li>{@code trim}: 앞뒤 공백 제거</li>
 *   <li>{@code substring(start, end)}: 부분 문자열 추출</li>
 *   <li>{@code toint}: 정수 변환</li>
 *   <li>{@code tolong}: long 변환</li>
 *   <li>{@code length}: 문자열 길이 반환</li>
 *   <li>{@code add(n)}: 덧셈</li>
 *   <li>{@code sub(n)}: 뺄셈</li>
 *   <li>{@code lower}: 소문자 변환</li>
 *   <li>{@code upper}: 대문자 변환</li>
 * </ul>
 */
public final class BusinessTransformSupport {

    private BusinessTransformSupport() {
        // 유틸리티 클래스 - 인스턴스 생성 불가
    }

    /**
     * transform spec에 따라 값을 변환합니다.
     *
     * <p>지원하지 않는 transform 이름이면 원래 값을 그대로 반환합니다.</p>
     *
     * @param value     변환 대상 값
     * @param transform 변환 스펙
     * @return 변환된 값
     */
    public static Object applyTransform(final Object value, final TransformSpec transform) {
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
     *
     * <p>구분자로 분할 후 지정된 인덱스의 토큰을 반환합니다.
     * 인덱스가 범위를 벗어나면 원래 값을 반환합니다.</p>
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
     *
     * <p>start/end 인덱스가 범위를 벗어나면 안전하게 클램핑합니다.
     * args가 비어 있으면 원래 값을 그대로 반환합니다.</p>
     *
     * <p>NOTE: {@code BusinessWorkflowFilterEvaluator} 버전 기준으로 통일합니다.
     * value가 null이면 null을 반환하고, args가 빈 경우는 별도 분기로 value를 반환합니다.
     * (이전 HybridResolver 버전은 두 조건을 합쳐 value를 반환했으나, null 명시 반환이 더 안전합니다.)</p>
     */
    private static Object transformSubstring(final Object value, final List<Object> args) {
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
     *
     * <p>숫자로 변환할 수 없는 값이 있으면 원래 값을 반환합니다.</p>
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
     * 객체를 BigDecimal로 변환합니다.
     *
     * <p>변환 실패 시 null을 반환합니다.</p>
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
     *
     * <p>변환 실패 또는 인덱스 범위 초과 시 defaultValue를 반환합니다.</p>
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
     * transforms 스펙 모델입니다.
     *
     * <p>compact text 문법 예시: {@code "split(',', 0)"}, {@code "toint"}, {@code "add(10)"}</p>
     *
     * <p>이 record는 {@code BusinessActionDataIndexHybridResolver}, {@code BusinessWorkflowFilterEvaluator},
     * {@code CollectDcdataTcAction}(Calculation Rule)에서 공통으로 사용합니다.</p>
     */
    public record TransformSpec(
            String name,
            List<Object> args
    ) {

        public TransformSpec {
            // name normalize 후 null 체크
            name = normalize(name);
            if (name == null) {
                throw new IllegalArgumentException("transform name is required");
            }
            name = name.toLowerCase(Locale.ROOT);
            // args null이면 빈 리스트, 아니면 방어적 복사
            args = args == null ? List.of() : List.copyOf(args);
        }

        /**
         * compact 문자열을 transform spec으로 파싱합니다.
         *
         * <p>괄호가 없으면 name만 있는 스펙(예: {@code "toint"})으로 파싱합니다.<br>
         * 괄호가 있으면 괄호 내 인수를 쉼표로 분리하고, 숫자 변환 가능한 인수는 BigDecimal로 파싱합니다.</p>
         *
         * @param compactText compact text 형식의 transform 표현식 (예: {@code "add(10)"})
         * @return 파싱된 TransformSpec
         * @throws IllegalArgumentException compactText가 blank인 경우
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
                final String trimmed = token.trim();
                final String unquoted = stripQuotes(trimmed);
                final BigDecimal numeric = toBigDecimalFromString(unquoted);
                args.add(numeric != null ? numeric : unquoted);
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

        /**
         * 문자열을 BigDecimal로 변환합니다.
         *
         * <p>변환 실패 시 null을 반환합니다.</p>
         */
        private static BigDecimal toBigDecimalFromString(final String value) {
            if (value == null) {
                return null;
            }
            try {
                return new BigDecimal(value.trim());
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
