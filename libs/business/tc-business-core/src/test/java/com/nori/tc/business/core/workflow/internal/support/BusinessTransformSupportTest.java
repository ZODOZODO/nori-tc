package com.nori.tc.business.core.workflow.internal.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link BusinessTransformSupport}의 transform 함수 10개 각각에 대한 단위 테스트입니다.
 *
 * <p>검증 범위:</p>
 * <ul>
 *   <li>정상 케이스: 각 transform 함수의 기본 동작</li>
 *   <li>null 케이스: value가 null일 때의 처리</li>
 *   <li>경계 케이스: 인덱스 범위 초과, 빈 args 등</li>
 *   <li>{@code transformSubstring}의 {@code args.isEmpty()} 방어 케이스</li>
 * </ul>
 */
@DisplayName("BusinessTransformSupport 단위 테스트")
class BusinessTransformSupportTest {

    // ─────────────────────────────────────────────────────────
    // TransformSpec 파싱 테스트
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("fromCompactText: 인수 없는 함수명은 args=빈 리스트로 파싱됩니다")
    void fromCompactText_functionNameOnly() {
        final BusinessTransformSupport.TransformSpec spec =
                BusinessTransformSupport.TransformSpec.fromCompactText("toint");
        assertEquals("toint", spec.name());
        assertEquals(List.of(), spec.args());
    }

    @Test
    @DisplayName("fromCompactText: 괄호 안 숫자 인수는 BigDecimal로 파싱됩니다")
    void fromCompactText_numericArgs() {
        final BusinessTransformSupport.TransformSpec spec =
                BusinessTransformSupport.TransformSpec.fromCompactText("add(10)");
        assertEquals("add", spec.name());
        assertEquals(1, spec.args().size());
        assertEquals(new BigDecimal("10"), spec.args().get(0));
    }

    @Test
    @DisplayName("fromCompactText: 따옴표로 감싼 문자열 인수는 따옴표를 제거합니다")
    void fromCompactText_quotedStringArgs() {
        // NOTE: 쉼표(,)는 argsSection 분리자이므로 compact text에서 직접 사용 불가.
        //       파이프(|)처럼 분리자로 쓰이지 않는 문자는 올바르게 파싱됩니다.
        final BusinessTransformSupport.TransformSpec spec =
                BusinessTransformSupport.TransformSpec.fromCompactText("split('|', 0)");
        assertEquals("split", spec.name());
        assertEquals("|", spec.args().get(0));
        assertEquals(new BigDecimal("0"), spec.args().get(1));
    }

    @Test
    @DisplayName("fromCompactText: blank 문자열은 IllegalArgumentException을 발생시킵니다")
    void fromCompactText_blank_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> BusinessTransformSupport.TransformSpec.fromCompactText("   "));
    }

    @Test
    @DisplayName("TransformSpec: name은 소문자로 정규화됩니다")
    void transformSpec_name_normalized_toLowercase() {
        final BusinessTransformSupport.TransformSpec spec =
                new BusinessTransformSupport.TransformSpec("TOINT", null);
        assertEquals("toint", spec.name());
    }

    @Test
    @DisplayName("TransformSpec: args=null이면 빈 리스트로 초기화됩니다")
    void transformSpec_nullArgs_becomesEmptyList() {
        final BusinessTransformSupport.TransformSpec spec =
                new BusinessTransformSupport.TransformSpec("trim", null);
        assertNotNull(spec.args());
        assertEquals(List.of(), spec.args());
    }

    // ─────────────────────────────────────────────────────────
    // 1. split
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("split: 구분자와 인덱스로 토큰을 반환합니다")
    void split_basicCase() {
        // NOTE: 쉼표(,)는 compact text 인수 분리자이므로 split 구분자로 직접 쓸 수 없습니다.
        //       파이프(|)를 구분자로 사용하여 인덱스 1번 토큰을 추출합니다.
        final Object result = BusinessTransformSupport.applyTransform(
                "A|B|C",
                BusinessTransformSupport.TransformSpec.fromCompactText("split('|', 1)")
        );
        assertEquals("B", result);
    }

    @Test
    @DisplayName("split: 인덱스가 범위를 벗어나면 원래 값을 반환합니다")
    void split_indexOutOfBounds_returnsOriginal() {
        final Object result = BusinessTransformSupport.applyTransform(
                "A,B",
                BusinessTransformSupport.TransformSpec.fromCompactText("split(',', 5)")
        );
        assertEquals("A,B", result);
    }

    @Test
    @DisplayName("split: value가 null이면 null을 반환합니다")
    void split_nullValue_returnsNull() {
        final Object result = BusinessTransformSupport.applyTransform(
                null,
                BusinessTransformSupport.TransformSpec.fromCompactText("split(',', 0)")
        );
        assertNull(result);
    }

    // ─────────────────────────────────────────────────────────
    // 2. trim
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("trim: 앞뒤 공백을 제거합니다")
    void trim_removesLeadingAndTrailingSpaces() {
        final Object result = BusinessTransformSupport.applyTransform(
                "  hello world  ",
                new BusinessTransformSupport.TransformSpec("trim", null)
        );
        assertEquals("hello world", result);
    }

    @Test
    @DisplayName("trim: value가 null이면 null을 반환합니다")
    void trim_nullValue_returnsNull() {
        final Object result = BusinessTransformSupport.applyTransform(
                null,
                new BusinessTransformSupport.TransformSpec("trim", null)
        );
        assertNull(result);
    }

    // ─────────────────────────────────────────────────────────
    // 3. substring
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("substring: start/end 인덱스로 부분 문자열을 추출합니다")
    void substring_basicCase() {
        final Object result = BusinessTransformSupport.applyTransform(
                "ABCDEFG",
                BusinessTransformSupport.TransformSpec.fromCompactText("substring(2, 5)")
        );
        assertEquals("CDE", result);
    }

    @Test
    @DisplayName("substring: args가 비어 있으면 원래 값을 그대로 반환합니다 (방어 케이스)")
    void substring_emptyArgs_returnsOriginal() {
        // args.isEmpty() 방어 케이스: FilterEvaluator 버전 기준으로 원래 값 반환
        final Object result = BusinessTransformSupport.applyTransform(
                "ABCDEFG",
                new BusinessTransformSupport.TransformSpec("substring", List.of())
        );
        assertEquals("ABCDEFG", result);
    }

    @Test
    @DisplayName("substring: 인덱스가 범위를 벗어나면 안전하게 클램핑합니다")
    void substring_outOfBoundsIndex_clampsSafely() {
        final Object result = BusinessTransformSupport.applyTransform(
                "ABC",
                BusinessTransformSupport.TransformSpec.fromCompactText("substring(0, 100)")
        );
        assertEquals("ABC", result);
    }

    @Test
    @DisplayName("substring: value가 null이면 null을 반환합니다")
    void substring_nullValue_returnsNull() {
        final Object result = BusinessTransformSupport.applyTransform(
                null,
                BusinessTransformSupport.TransformSpec.fromCompactText("substring(0, 3)")
        );
        assertNull(result);
    }

    // ─────────────────────────────────────────────────────────
    // 4. toint
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("toint: 숫자 문자열을 정수로 변환합니다")
    void toint_numericString_convertsToInt() {
        final Object result = BusinessTransformSupport.applyTransform(
                "42",
                new BusinessTransformSupport.TransformSpec("toint", null)
        );
        assertEquals(42, result);
    }

    @Test
    @DisplayName("toint: 소수 문자열은 정수 부분만 반환합니다")
    void toint_decimalString_truncatesToInt() {
        final Object result = BusinessTransformSupport.applyTransform(
                "3.9",
                new BusinessTransformSupport.TransformSpec("toint", null)
        );
        assertEquals(3, result);
    }

    @Test
    @DisplayName("toint: 숫자로 변환 불가한 값은 null을 반환합니다")
    void toint_nonNumeric_returnsNull() {
        final Object result = BusinessTransformSupport.applyTransform(
                "ABC",
                new BusinessTransformSupport.TransformSpec("toint", null)
        );
        assertNull(result);
    }

    // ─────────────────────────────────────────────────────────
    // 5. tolong
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("tolong: 큰 정수 문자열을 long으로 변환합니다")
    void tolong_largeNumber_convertsToLong() {
        final Object result = BusinessTransformSupport.applyTransform(
                "9876543210",
                new BusinessTransformSupport.TransformSpec("tolong", null)
        );
        assertEquals(9876543210L, result);
    }

    @Test
    @DisplayName("tolong: value가 null이면 null을 반환합니다")
    void tolong_nullValue_returnsNull() {
        final Object result = BusinessTransformSupport.applyTransform(
                null,
                new BusinessTransformSupport.TransformSpec("tolong", null)
        );
        assertNull(result);
    }

    // ─────────────────────────────────────────────────────────
    // 6. length
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("length: 문자열의 길이를 반환합니다")
    void length_returnsStringLength() {
        final Object result = BusinessTransformSupport.applyTransform(
                "HELLO",
                new BusinessTransformSupport.TransformSpec("length", null)
        );
        assertEquals(5, result);
    }

    @Test
    @DisplayName("length: value가 null이면 null을 반환합니다")
    void length_nullValue_returnsNull() {
        final Object result = BusinessTransformSupport.applyTransform(
                null,
                new BusinessTransformSupport.TransformSpec("length", null)
        );
        assertNull(result);
    }

    // ─────────────────────────────────────────────────────────
    // 7. add
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("add: 숫자 값에 인수를 더합니다")
    void add_addsArgToValue() {
        final Object result = BusinessTransformSupport.applyTransform(
                "100",
                BusinessTransformSupport.TransformSpec.fromCompactText("add(25)")
        );
        assertInstanceOf(BigDecimal.class, result);
        assertEquals(new BigDecimal("125"), result);
    }

    @Test
    @DisplayName("add: args가 비어 있으면 원래 값을 반환합니다")
    void add_emptyArgs_returnsOriginal() {
        final Object result = BusinessTransformSupport.applyTransform(
                "100",
                new BusinessTransformSupport.TransformSpec("add", List.of())
        );
        assertEquals("100", result);
    }

    @Test
    @DisplayName("add: value가 null이면 null을 반환합니다")
    void add_nullValue_returnsNull() {
        final Object result = BusinessTransformSupport.applyTransform(
                null,
                BusinessTransformSupport.TransformSpec.fromCompactText("add(5)")
        );
        assertNull(result);
    }

    // ─────────────────────────────────────────────────────────
    // 8. sub
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("sub: 숫자 값에서 인수를 뺍니다")
    void sub_subtractsArgFromValue() {
        final Object result = BusinessTransformSupport.applyTransform(
                "100",
                BusinessTransformSupport.TransformSpec.fromCompactText("sub(30)")
        );
        assertInstanceOf(BigDecimal.class, result);
        assertEquals(new BigDecimal("70"), result);
    }

    @Test
    @DisplayName("sub: 숫자로 변환 불가한 값은 원래 값을 반환합니다")
    void sub_nonNumericValue_returnsOriginal() {
        final Object result = BusinessTransformSupport.applyTransform(
                "INVALID",
                BusinessTransformSupport.TransformSpec.fromCompactText("sub(10)")
        );
        assertEquals("INVALID", result);
    }

    // ─────────────────────────────────────────────────────────
    // 9. lower
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("lower: 문자열을 소문자로 변환합니다")
    void lower_convertsToLowercase() {
        final Object result = BusinessTransformSupport.applyTransform(
                "HELLO World",
                new BusinessTransformSupport.TransformSpec("lower", null)
        );
        assertEquals("hello world", result);
    }

    @Test
    @DisplayName("lower: value가 null이면 null을 반환합니다")
    void lower_nullValue_returnsNull() {
        final Object result = BusinessTransformSupport.applyTransform(
                null,
                new BusinessTransformSupport.TransformSpec("lower", null)
        );
        assertNull(result);
    }

    // ─────────────────────────────────────────────────────────
    // 10. upper
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("upper: 문자열을 대문자로 변환합니다")
    void upper_convertsToUppercase() {
        final Object result = BusinessTransformSupport.applyTransform(
                "hello world",
                new BusinessTransformSupport.TransformSpec("upper", null)
        );
        assertEquals("HELLO WORLD", result);
    }

    @Test
    @DisplayName("upper: value가 null이면 null을 반환합니다")
    void upper_nullValue_returnsNull() {
        final Object result = BusinessTransformSupport.applyTransform(
                null,
                new BusinessTransformSupport.TransformSpec("upper", null)
        );
        assertNull(result);
    }

    // ─────────────────────────────────────────────────────────
    // 미지원 transform
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("알 수 없는 transform 이름은 원래 값을 그대로 반환합니다")
    void unknown_transform_returnsOriginalValue() {
        final Object result = BusinessTransformSupport.applyTransform(
                "original",
                new BusinessTransformSupport.TransformSpec("unknown_func", null)
        );
        assertEquals("original", result);
    }
}
