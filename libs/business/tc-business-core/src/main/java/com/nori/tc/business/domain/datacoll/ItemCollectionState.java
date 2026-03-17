package com.nori.tc.business.domain.datacoll;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nori.tc.db.domain.common.model.DcopCollectionRule;

import java.math.BigDecimal;

/**
 * 단일 DCOP Item의 누적 수집 상태 record입니다.
 *
 * <p>Collection Rule에 따라 사용하는 필드가 다릅니다.</p>
 * <ul>
 *   <li>FIRST / LAST / MIN / MAX: {@code value} 필드 사용. {@code count}와 {@code sum}은 null/0.</li>
 *   <li>AVERAGE: {@code count}와 {@code sum} 필드 사용. 최종값 = sum / count. {@code value}는 null.</li>
 * </ul>
 *
 * <p>Redis 저장 시 JSON 직렬화되며, {@code @JsonCreator}를 통해 역직렬화됩니다.</p>
 */
public record ItemCollectionState(
        @JsonProperty("rule") DcopCollectionRule rule,
        @JsonProperty("count") long count,
        @JsonProperty("sum") BigDecimal sum,
        @JsonProperty("value") String value
) {

    /**
     * Jackson 역직렬화를 위한 생성자입니다.
     *
     * @param rule  Collection Rule
     * @param count AVERAGE 규칙의 수집 횟수
     * @param sum   AVERAGE 규칙의 누적 합계
     * @param value FIRST/LAST/MIN/MAX 규칙의 수집값
     */
    @JsonCreator
    public ItemCollectionState(
            @JsonProperty("rule") final DcopCollectionRule rule,
            @JsonProperty("count") final long count,
            @JsonProperty("sum") final BigDecimal sum,
            @JsonProperty("value") final String value
    ) {
        this.rule = rule;
        this.count = count;
        this.sum = sum;
        this.value = value;
    }

    /**
     * FIRST / LAST / MIN / MAX 규칙에서 value만 설정하여 생성합니다.
     *
     * @param rule  Collection Rule (FIRST, LAST, MIN, MAX 중 하나)
     * @param value 수집된 값
     * @return 생성된 ItemCollectionState
     */
    public static ItemCollectionState ofValue(final DcopCollectionRule rule, final String value) {
        return new ItemCollectionState(rule, 0L, null, value);
    }

    /**
     * AVERAGE 규칙의 첫 번째 수집값으로 초기화합니다.
     *
     * @param firstValue 첫 번째 수집된 BigDecimal 값
     * @return count=1, sum=firstValue로 초기화된 ItemCollectionState
     */
    public static ItemCollectionState ofAverageFirst(final BigDecimal firstValue) {
        return new ItemCollectionState(DcopCollectionRule.AVERAGE, 1L, firstValue, null);
    }

    /**
     * AVERAGE 규칙의 기존 상태에서 새 값을 누적합니다.
     *
     * @param additionalValue 누적할 BigDecimal 값
     * @return count+1, sum+additionalValue 로 갱신된 새 ItemCollectionState
     */
    public ItemCollectionState accumulateAverage(final BigDecimal additionalValue) {
        final BigDecimal newSum = (this.sum != null ? this.sum : BigDecimal.ZERO).add(additionalValue);
        return new ItemCollectionState(DcopCollectionRule.AVERAGE, this.count + 1, newSum, null);
    }
}
