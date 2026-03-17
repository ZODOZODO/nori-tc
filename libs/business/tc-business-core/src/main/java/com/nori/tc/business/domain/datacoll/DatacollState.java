package com.nori.tc.business.domain.datacoll;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * DATACOLL 수집 상태 도메인 객체입니다.
 *
 * <p>DCSPECREQ_REP 수신 시점부터 DATACOLL 보고 완료까지 lot 단위로 유지되며,
 * Redis에 JSON 형태로 직렬화되어 저장됩니다.</p>
 *
 * <ul>
 *   <li>{@code dcspecValue}: MES가 수집 요청한 항목 목록. key=dcopItemName, value=수집된 최종값(초기: "")</li>
 *   <li>{@code collectionState}: 각 DCOP Item의 누적 수집 상태. key=dcopItemName</li>
 * </ul>
 *
 * <p>AVERAGE 규칙은 sum/count 집계 방식을 사용하므로 COLLECT_DCDATA 실행 횟수와 무관하게 저장 크기가 고정됩니다.</p>
 *
 * <p>Redis Key 포맷: {@code tc:business:core:datacoll:{eqpId}:{correlationId}}</p>
 */
public class DatacollState {

    /**
     * MES가 요청한 수집 항목 목록입니다. key=dcopItemName, value=최종 수집값 (초기: "").
     *
     * <p>COLLECT_DCDATA가 실행될 때마다 해당 항목의 값이 누적되며,
     * DATACOLL action 시점에 최종값으로 채워집니다.</p>
     */
    @JsonProperty("dcspecValue")
    private Map<String, String> dcspecValue;

    /**
     * 각 항목의 누적 수집 상태입니다. key=dcopItemName.
     *
     * <p>COLLECT_DCDATA가 실행될 때마다 Collection Rule에 따라 갱신됩니다.</p>
     */
    @JsonProperty("collectionState")
    private Map<String, ItemCollectionState> collectionState;

    /**
     * Jackson 역직렬화를 위한 기본 생성자입니다.
     */
    public DatacollState() {
        this.dcspecValue = new HashMap<>();
        this.collectionState = new HashMap<>();
    }

    /**
     * 수집 항목 key 목록과 초기 수집 상태를 지정하여 생성합니다.
     *
     * @param dcspecValue     MES가 요청한 항목 목록 (key=dcopItemName, value는 초기에 "")
     * @param collectionState 각 항목의 누적 수집 상태
     */
    @JsonCreator
    public DatacollState(
            @JsonProperty("dcspecValue") final Map<String, String> dcspecValue,
            @JsonProperty("collectionState") final Map<String, ItemCollectionState> collectionState
    ) {
        this.dcspecValue = dcspecValue != null ? new HashMap<>(dcspecValue) : new HashMap<>();
        this.collectionState = collectionState != null ? new HashMap<>(collectionState) : new HashMap<>();
    }

    /**
     * DCSPECREQ_REP 수신 시 dcspecValue key 목록으로 초기 DatacollState를 생성합니다.
     *
     * <p>모든 value는 ""로 초기화되고, collectionState는 빈 Map으로 시작합니다.</p>
     *
     * @param receivedDcspecValue DCSPECREQ_REP payload에서 수신한 dcspecValue ({key: ""} 형태)
     * @return 초기화된 DatacollState
     */
    public static DatacollState initFrom(final Map<String, String> receivedDcspecValue) {
        final Map<String, String> dcspecValue = new HashMap<>();
        if (receivedDcspecValue != null) {
            for (final String key : receivedDcspecValue.keySet()) {
                dcspecValue.put(key, "");
            }
        }
        return new DatacollState(dcspecValue, new HashMap<>());
    }

    /**
     * dcspecValue를 반환합니다.
     *
     * @return key=dcopItemName, value=수집된 최종값 Map
     */
    public Map<String, String> getDcspecValue() {
        return dcspecValue;
    }

    /**
     * dcspecValue를 설정합니다.
     *
     * @param dcspecValue 수집 항목 Map
     */
    public void setDcspecValue(final Map<String, String> dcspecValue) {
        this.dcspecValue = dcspecValue;
    }

    /**
     * collectionState를 반환합니다.
     *
     * @return key=dcopItemName, value=누적 수집 상태 Map
     */
    public Map<String, ItemCollectionState> getCollectionState() {
        return collectionState;
    }

    /**
     * collectionState를 설정합니다.
     *
     * @param collectionState 누적 수집 상태 Map
     */
    public void setCollectionState(final Map<String, ItemCollectionState> collectionState) {
        this.collectionState = collectionState;
    }
}
