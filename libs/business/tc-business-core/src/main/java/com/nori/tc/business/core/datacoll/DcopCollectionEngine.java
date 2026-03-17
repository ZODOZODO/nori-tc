package com.nori.tc.business.core.datacoll;

import com.nori.tc.business.core.workflow.internal.support.BusinessTransformSupport;
import com.nori.tc.business.domain.datacoll.DatacollState;
import com.nori.tc.business.domain.datacoll.ItemCollectionState;
import com.nori.tc.db.domain.model.TcModelDcopItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DCOP Item 수집 결과의 최종값을 결정하는 엔진 컴포넌트입니다.
 *
 * <p>이 클래스는 {@link DatacollState}의 {@code collectionState}와 {@link TcModelDcopItem} 목록을 받아
 * 각 항목의 최종값을 결정합니다. {@code DATACOLL} TCAction에서 호출되며,
 * {@code CollectDcdataTcAction}에서도 재사용 가능합니다.</p>
 *
 * <p>처리 순서:
 * <ol>
 *   <li>{@code orderRule ASC, dcopItemName ASC} 기준으로 DCOP Item을 정렬합니다.</li>
 *   <li>각 항목의 {@code collectionState}에서 Collection Rule 기반 최종값을 결정합니다.</li>
 *   <li>{@code calculationRule}이 있으면 {@link BusinessTransformSupport#applyTransform}을 적용합니다.</li>
 *   <li>{@code collectionState}에 없는 항목은 빈 문자열({@code ""})로 처리합니다.</li>
 * </ol>
 * </p>
 *
 * <p>재사용 대상:
 * <ul>
 *   <li>{@code DatacollTcAction} (T5): MES 보고 전 최종값 결정</li>
 * </ul>
 * </p>
 */
@Component
public class DcopCollectionEngine {

    private static final Logger log = LoggerFactory.getLogger(DcopCollectionEngine.class);

    /**
     * collectionState와 dcopItems를 기반으로 각 항목의 최종값을 결정합니다.
     *
     * <p>정렬 기준: {@code orderRule ASC, dcopItemName ASC}</p>
     *
     * <p>처리 규칙:
     * <ul>
     *   <li>FIRST / LAST / MIN / MAX: {@code collectionState.value} 그대로 사용</li>
     *   <li>AVERAGE: {@code sum / count} 계산 (count가 0이면 {@code ""})</li>
     *   <li>calculationRule이 null이 아니면 {@link BusinessTransformSupport#applyTransform} 후처리</li>
     *   <li>collectionState에 없는 항목은 {@code ""} 반환</li>
     * </ul>
     * </p>
     *
     * @param state     Redis에서 읽은 DatacollState
     * @param dcopItems 해당 모델의 DCOP Item 목록
     * @return key=dcopItemName, value=최종값 Map. 순서는 orderRule ASC, dcopItemName ASC
     */
    public Map<String, String> resolveFinalValues(
            final DatacollState state,
            final List<TcModelDcopItem> dcopItems
    ) {
        final Map<String, String> finalValues = new LinkedHashMap<>();
        final Map<String, ItemCollectionState> collectionState = state.getCollectionState();

        // orderRule ASC, dcopItemName ASC 정렬 (DB 조회 시 정렬되어 오지만 명시적으로 보장)
        final List<TcModelDcopItem> sorted = dcopItems.stream()
                .sorted(Comparator
                        .comparingInt((TcModelDcopItem item) -> item.orderRule() != null ? item.orderRule() : 0)
                        .thenComparing(TcModelDcopItem::dcopItemName))
                .toList();

        for (final TcModelDcopItem item : sorted) {
            final String dcopItemName = item.dcopItemName();
            final ItemCollectionState cs = collectionState.get(dcopItemName);

            if (cs == null) {
                // collectionState에 없는 항목은 빈 문자열로 처리 (COLLECT_DCDATA가 실행되지 않은 경우)
                log.warn("collectionState에 항목이 없습니다. dcopItemName={}를 빈 문자열로 처리합니다.", dcopItemName);
                finalValues.put(dcopItemName, "");
                continue;
            }

            // Collection Rule에 따른 최종값 결정
            String finalValue = resolveFinalValue(dcopItemName, cs);

            // Calculation Rule 후처리 (calculationRule이 null이 아닌 경우)
            if (item.calculationRule() != null && !item.calculationRule().isBlank()) {
                finalValue = applyCalculationRule(dcopItemName, item.calculationRule(), finalValue);
            }

            finalValues.put(dcopItemName, finalValue);
        }

        return finalValues;
    }

    /**
     * Collection Rule에 따른 최종값을 결정합니다.
     *
     * <p>FIRST / LAST / MIN / MAX: {@code value} 그대로 반환<br>
     * AVERAGE: {@code sum / count} 계산. count가 0이면 {@code ""} 반환.</p>
     *
     * @param dcopItemName 항목명 (로그용)
     * @param cs           ItemCollectionState
     * @return 최종값 문자열. null인 경우 {@code ""}
     */
    private String resolveFinalValue(final String dcopItemName, final ItemCollectionState cs) {
        if (cs.rule() == null) {
            log.warn("collectionRule이 null입니다. dcopItemName={}를 빈 문자열로 처리합니다.", dcopItemName);
            return "";
        }

        return switch (cs.rule()) {
            case FIRST, LAST, MIN, MAX -> cs.value() != null ? cs.value() : "";
            case AVERAGE -> {
                if (cs.count() <= 0 || cs.sum() == null) {
                    yield "";
                }
                // AVERAGE 최종값: sum / count (고정 소수점 15자리, DECIMAL128 정밀도)
                yield cs.sum()
                        .divide(BigDecimal.valueOf(cs.count()), MathContext.DECIMAL128)
                        .stripTrailingZeros()
                        .toPlainString();
            }
        };
    }

    /**
     * Calculation Rule을 적용하여 값을 변환합니다.
     *
     * <p>적용 실패 시 기존 값을 유지하고 WARN 로그를 남깁니다.</p>
     *
     * @param dcopItemName    항목명 (로그용)
     * @param calculationRule transform compact text 형식의 계산 규칙
     * @param currentValue    현재 최종값
     * @return 변환된 값. 실패 시 currentValue 그대로
     */
    private String applyCalculationRule(
            final String dcopItemName,
            final String calculationRule,
            final String currentValue
    ) {
        try {
            final BusinessTransformSupport.TransformSpec spec =
                    BusinessTransformSupport.TransformSpec.fromCompactText(calculationRule);
            final Object transformed = BusinessTransformSupport.applyTransform(currentValue, spec);
            return transformed != null ? String.valueOf(transformed) : currentValue;
        } catch (Exception ex) {
            log.warn("calculationRule 적용 실패. 기존 값을 유지합니다. dcopItemName={}, calculationRule={}",
                    dcopItemName, calculationRule, ex);
            return currentValue;
        }
    }
}
