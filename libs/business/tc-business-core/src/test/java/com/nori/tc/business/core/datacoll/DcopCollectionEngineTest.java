package com.nori.tc.business.core.datacoll;

import com.nori.tc.business.domain.datacoll.DatacollState;
import com.nori.tc.business.domain.datacoll.ItemCollectionState;
import com.nori.tc.db.domain.common.model.DcopCollectionRule;
import com.nori.tc.db.domain.model.TcModelDcopItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DcopCollectionEngine} 단위 테스트입니다.
 *
 * <p>{@code resolveFinalValues()} 메서드의 Collection Rule별 최종값 결정, Calculation Rule 적용,
 * Order Rule 정렬 순서, collectionState 없는 항목 처리를 검증합니다.</p>
 */
@DisplayName("DcopCollectionEngine 단위 테스트")
class DcopCollectionEngineTest {

    private DcopCollectionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DcopCollectionEngine();
    }

    // -------------------------------------------------------------------------
    // Collection Rule 최종값 결정
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("FIRST Rule: value를 그대로 최종값으로 반환")
    void resolveFinalValues_firstRule_shouldReturnValue() {
        // given
        final DatacollState state = stateWithCollectionState(Map.of(
                "Temperature", ItemCollectionState.ofValue(DcopCollectionRule.FIRST, "23.5")
        ));
        final List<TcModelDcopItem> items = List.of(
                dcopItem("Temperature", null, 0)
        );

        // when
        final Map<String, String> result = engine.resolveFinalValues(state, items);

        // then
        assertEquals("23.5", result.get("Temperature"), "FIRST Rule: value 그대로 반환해야 합니다");
    }

    @Test
    @DisplayName("LAST Rule: value를 그대로 최종값으로 반환")
    void resolveFinalValues_lastRule_shouldReturnValue() {
        // given
        final DatacollState state = stateWithCollectionState(Map.of(
                "Humidity", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "60.0")
        ));
        final List<TcModelDcopItem> items = List.of(
                dcopItem("Humidity", null, 0)
        );

        // when
        final Map<String, String> result = engine.resolveFinalValues(state, items);

        // then
        assertEquals("60.0", result.get("Humidity"), "LAST Rule: value 그대로 반환해야 합니다");
    }

    @Test
    @DisplayName("AVERAGE Rule: sum/count로 평균 계산")
    void resolveFinalValues_averageRule_shouldCalculateAverage() {
        // given - count=3, sum=117.0 → 평균 39.0
        final ItemCollectionState avgState = new ItemCollectionState(
                DcopCollectionRule.AVERAGE, 3L, new BigDecimal("117.0"), null);
        final DatacollState state = stateWithCollectionState(Map.of("Pressure", avgState));
        final List<TcModelDcopItem> items = List.of(dcopItem("Pressure", null, 0));

        // when
        final Map<String, String> result = engine.resolveFinalValues(state, items);

        // then
        assertEquals("39", result.get("Pressure"), "AVERAGE Rule: sum(117)/count(3)=39이어야 합니다");
    }

    @Test
    @DisplayName("AVERAGE Rule: count=0이면 빈 문자열 반환")
    void resolveFinalValues_averageRule_whenCountIsZero_shouldReturnEmpty() {
        // given - count=0 (수집값 없음)
        final ItemCollectionState avgState = new ItemCollectionState(
                DcopCollectionRule.AVERAGE, 0L, null, null);
        final DatacollState state = stateWithCollectionState(Map.of("Pressure", avgState));
        final List<TcModelDcopItem> items = List.of(dcopItem("Pressure", null, 0));

        // when
        final Map<String, String> result = engine.resolveFinalValues(state, items);

        // then
        assertEquals("", result.get("Pressure"), "AVERAGE Rule: count=0이면 빈 문자열이어야 합니다");
    }

    @Test
    @DisplayName("MIN Rule: value를 그대로 최종값으로 반환")
    void resolveFinalValues_minRule_shouldReturnValue() {
        // given
        final DatacollState state = stateWithCollectionState(Map.of(
                "Pressure", ItemCollectionState.ofValue(DcopCollectionRule.MIN, "1010.0")
        ));
        final List<TcModelDcopItem> items = List.of(dcopItem("Pressure", null, 0));

        // when
        final Map<String, String> result = engine.resolveFinalValues(state, items);

        // then
        assertEquals("1010.0", result.get("Pressure"), "MIN Rule: 최솟값 그대로 반환해야 합니다");
    }

    @Test
    @DisplayName("MAX Rule: value를 그대로 최종값으로 반환")
    void resolveFinalValues_maxRule_shouldReturnValue() {
        // given
        final DatacollState state = stateWithCollectionState(Map.of(
                "Temperature", ItemCollectionState.ofValue(DcopCollectionRule.MAX, "35.7")
        ));
        final List<TcModelDcopItem> items = List.of(dcopItem("Temperature", null, 0));

        // when
        final Map<String, String> result = engine.resolveFinalValues(state, items);

        // then
        assertEquals("35.7", result.get("Temperature"), "MAX Rule: 최댓값 그대로 반환해야 합니다");
    }

    // -------------------------------------------------------------------------
    // collectionState 없는 항목
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("collectionState에 없는 항목은 빈 문자열로 반환")
    void resolveFinalValues_whenCollectionStateAbsent_shouldReturnEmpty() {
        // given - collectionState에 Temperature 없음
        final DatacollState state = stateWithCollectionState(Map.of());
        final List<TcModelDcopItem> items = List.of(dcopItem("Temperature", null, 0));

        // when
        final Map<String, String> result = engine.resolveFinalValues(state, items);

        // then
        assertEquals("", result.get("Temperature"),
                "collectionState 없는 항목은 빈 문자열이어야 합니다");
    }

    // -------------------------------------------------------------------------
    // Calculation Rule 적용
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("calculationRule add(10): 수집값에 10을 더한 값 반환")
    void resolveFinalValues_withCalculationRuleAdd_shouldApplyTransform() {
        // given - LAST value = "23.5", calculationRule = "add(10)"
        final DatacollState state = stateWithCollectionState(Map.of(
                "Temperature", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "23.5")
        ));
        final List<TcModelDcopItem> items = List.of(dcopItem("Temperature", "add(10)", 0));

        // when
        final Map<String, String> result = engine.resolveFinalValues(state, items);

        // then
        assertEquals("33.5", result.get("Temperature"),
                "calculationRule add(10): 23.5 + 10 = 33.5이어야 합니다");
    }

    @Test
    @DisplayName("calculationRule toint: 소수를 정수로 변환")
    void resolveFinalValues_withCalculationRuleToint_shouldConvertToInt() {
        // given - value = "23.7", calculationRule = "toint"
        final DatacollState state = stateWithCollectionState(Map.of(
                "Temperature", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "23.7")
        ));
        final List<TcModelDcopItem> items = List.of(dcopItem("Temperature", "toint", 0));

        // when
        final Map<String, String> result = engine.resolveFinalValues(state, items);

        // then
        assertEquals("23", result.get("Temperature"),
                "calculationRule toint: 23.7을 정수 변환하면 23이어야 합니다");
    }

    @Test
    @DisplayName("calculationRule 파싱 실패 시 기존 값 유지")
    void resolveFinalValues_whenCalculationRuleFails_shouldKeepOriginalValue() {
        // given - 유효하지 않은 calculationRule
        final DatacollState state = stateWithCollectionState(Map.of(
                "Temperature", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "23.5")
        ));
        // calculationRule이 blank → fromCompactText에서 예외 발생
        final List<TcModelDcopItem> items = List.of(dcopItem("Temperature", "   ", 0));

        // when
        final Map<String, String> result = engine.resolveFinalValues(state, items);

        // then - calculationRule이 blank이면 적용되지 않으므로 원래 값 그대로
        assertEquals("23.5", result.get("Temperature"),
                "blank calculationRule은 건너뜁니다 (isBlank() 체크로 적용 생략)");
    }

    // -------------------------------------------------------------------------
    // Order Rule 정렬 순서
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Order Rule: orderRule ASC, dcopItemName ASC 순서로 정렬")
    void resolveFinalValues_shouldRespectOrderRule() {
        // given - orderRule 역순으로 입력, 정렬 결과 확인
        final DatacollState state = stateWithCollectionState(Map.of(
                "AAA", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "1"),
                "BBB", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "2"),
                "CCC", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "3"),
                "DDD", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "4")
        ));
        // 순서를 뒤섞어 입력: orderRule=1/DDD, orderRule=0/BBB, orderRule=0/AAA, orderRule=1/CCC
        final List<TcModelDcopItem> items = List.of(
                dcopItem("DDD", null, 1),
                dcopItem("BBB", null, 0),
                dcopItem("AAA", null, 0),
                dcopItem("CCC", null, 1)
        );

        // when
        final Map<String, String> result = engine.resolveFinalValues(state, items);

        // then - LinkedHashMap 삽입 순서: AAA, BBB, CCC, DDD (orderRule ASC, name ASC)
        final List<String> keys = List.copyOf(result.keySet());
        assertEquals(List.of("AAA", "BBB", "CCC", "DDD"), keys,
                "Order Rule 정렬 기준: orderRule ASC, dcopItemName ASC이어야 합니다");
    }

    @Test
    @DisplayName("orderRule이 null이면 0으로 처리")
    void resolveFinalValues_whenOrderRuleIsNull_shouldTreatAsZero() {
        // given - orderRule=null → 0으로 처리
        final DatacollState state = stateWithCollectionState(Map.of(
                "ZZZ", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "z"),
                "AAA", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "a")
        ));
        final List<TcModelDcopItem> items = List.of(
                dcopItem("ZZZ", null, null),   // orderRule = null → 0
                dcopItem("AAA", null, null)    // orderRule = null → 0
        );

        // when
        final Map<String, String> result = engine.resolveFinalValues(state, items);

        // then - null orderRule은 0으로 처리하므로 dcopItemName ASC: AAA, ZZZ
        final List<String> keys = List.copyOf(result.keySet());
        assertEquals(List.of("AAA", "ZZZ"), keys,
                "orderRule=null은 0으로 처리되어 name ASC 정렬되어야 합니다");
    }

    // -------------------------------------------------------------------------
    // 테스트 헬퍼
    // -------------------------------------------------------------------------

    /**
     * collectionState가 설정된 DatacollState를 생성합니다.
     */
    private static DatacollState stateWithCollectionState(
            final Map<String, ItemCollectionState> collectionState
    ) {
        final DatacollState state = new DatacollState();
        state.setCollectionState(new HashMap<>(collectionState));
        return state;
    }

    /**
     * TcModelDcopItem 테스트 픽스처를 생성합니다.
     * variableId는 dcopItemName과 동일하게 설정합니다.
     */
    private static TcModelDcopItem dcopItem(
            final String dcopItemName,
            final String calculationRule,
            final Integer orderRule
    ) {
        return new TcModelDcopItem(
                1L, 100L, dcopItemName, "WORKFLOW_A",
                null, dcopItemName, DcopCollectionRule.LAST,
                calculationRule, orderRule, OffsetDateTime.now()
        );
    }
}
