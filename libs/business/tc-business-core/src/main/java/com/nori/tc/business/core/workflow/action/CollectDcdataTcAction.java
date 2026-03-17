package com.nori.tc.business.core.workflow.action;

import com.nori.tc.business.core.datacoll.DatacollStatePort;
import com.nori.tc.business.core.datacoll.DcopItemPort;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.action.AbstractMesActionExecutor;
import com.nori.tc.business.action.TcAction;
import com.nori.tc.business.core.workflow.internal.support.BusinessWorkflowCommandSupport;
import com.nori.tc.business.domain.datacoll.DatacollState;
import com.nori.tc.business.domain.datacoll.ItemCollectionState;
import com.nori.tc.db.domain.common.model.DcopCollectionRule;
import com.nori.tc.db.domain.model.TcModelDcopItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * COLLECT_DCDATA workflow action을 처리하는 MES 액션 실행기입니다.
 *
 * <p>workflow에 명시적으로 배치하는 수집 action입니다.
 * 이 action이 실행될 때 현재 {@code workflowName}과 일치하는 DCOP Item의 값을 수집하고
 * Redis {@code collectionState}를 갱신합니다.</p>
 *
 * <p>이벤트 발생마다 자동으로 수집되지 않으며, workflow 정의에 이 action이 있을 때만 수집이 실행됩니다.</p>
 *
 * <p>Collection Rule 별 갱신 방식:
 * <ul>
 *   <li>FIRST: 해당 항목의 collectionState가 없을 때만 저장. 이후 실행 무시</li>
 *   <li>LAST: 매 실행마다 value 덮어쓰기</li>
 *   <li>AVERAGE: count += 1, sum += 수집값 (숫자 변환 불가 시 건너뜀)</li>
 *   <li>MIN: value 없으면 저장. 있으면 수집값 < 현재 최솟값일 때만 갱신</li>
 *   <li>MAX: value 없으면 저장. 있으면 수집값 > 현재 최댓값일 때만 갱신</li>
 * </ul>
 * </p>
 *
 * <p>실패 정책:
 * <ul>
 *   <li>Redis에 DatacollState 없음: WARN 로그 후 수집 건너뜀 (DCSPECREQ_REP 없이 진행 중인 lot)</li>
 *   <li>variableId에 해당하는 값 없음: 해당 DCOP Item 건너뜀, DEBUG 로그</li>
 *   <li>Redis 갱신 실패: 예외 전파</li>
 * </ul>
 * </p>
 */
@Component
public class CollectDcdataTcAction extends AbstractMesActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(CollectDcdataTcAction.class);

    private final DatacollStatePort datacollStatePort;
    private final DcopItemPort dcopItemPort;

    /**
     * 필수 의존성을 주입받습니다.
     *
     * @param datacollStatePort DATACOLL 수집 상태 저장소 포트
     * @param dcopItemPort      DCOP Item 조회 포트
     */
    public CollectDcdataTcAction(
            final DatacollStatePort datacollStatePort,
            final DcopItemPort dcopItemPort
    ) {
        this.datacollStatePort = Objects.requireNonNull(datacollStatePort, "datacollStatePort is null");
        this.dcopItemPort = Objects.requireNonNull(dcopItemPort, "dcopItemPort is null");
    }

    /**
     * COLLECT_DCDATA action을 실행합니다.
     *
     * <p>처리 흐름:
     * <ol>
     *   <li>eqpId, correlationId, workflowName 추출</li>
     *   <li>Redis에서 DatacollState 조회 (없으면 WARN + 건너뜀)</li>
     *   <li>modelVersionKey 기준 DCOP Item 조회 후 workflowName 필터링</li>
     *   <li>각 DCOP Item의 variableId 값 추출 → Collection Rule에 따라 collectionState 갱신</li>
     *   <li>DatacollStatePort.update()로 Redis 갱신</li>
     * </ol>
     * </p>
     *
     * @param context 액션 실행 컨텍스트
     */
    @TcAction("COLLECT_DCDATA")
    public void collectDcdata(final BusinessWorkflowActionContext context) {
        final String eqpId = context.record().eqpId();
        final String correlationId = BusinessWorkflowCommandSupport.resolveCorrelationId(context);
        final String workflowName = context.workflowEntry().workflowName();

        if (correlationId == null) {
            log.warn("correlationId 누락. COLLECT_DCDATA를 건너뜁니다. eqpId={}, workflowName={}",
                    eqpId, workflowName);
            return;
        }

        // Redis에서 DatacollState 조회
        final Optional<DatacollState> stateOptional = datacollStatePort.findByKey(eqpId, correlationId);
        if (stateOptional.isEmpty()) {
            // DCSPECREQ_REP를 받지 않은 lot이거나 TTL 만료된 경우
            log.warn("DatacollState not found. eqpId={}, correlationId={}. DCSPECREQ_REP 없이 진행 중인 lot으로 간주하여 수집을 건너뜁니다.",
                    eqpId, correlationId);
            return;
        }

        final DatacollState state = stateOptional.get();
        final long modelVersionKey = context.modelRuntime().modelVersionKey();

        // modelVersionKey 기준 DCOP Item 전체 조회 후 workflowName 필터링
        // 정렬 기준: orderRule ASC, dcopItemName ASC (포트에서 보장)
        final List<TcModelDcopItem> allDcopItems = dcopItemPort.findAllByModelVersionKey(modelVersionKey);
        final List<TcModelDcopItem> matchedItems = allDcopItems.stream()
                .filter(item -> workflowName.equals(item.workflowName()))
                .toList();

        if (matchedItems.isEmpty()) {
            log.debug("workflowName={}에 해당하는 DCOP Item이 없습니다. modelVersionKey={}",
                    workflowName, modelVersionKey);
            return;
        }

        // 각 DCOP Item에 대해 값 수집 및 collectionState 갱신
        final Map<String, ItemCollectionState> collectionState = state.getCollectionState();
        for (final TcModelDcopItem item : matchedItems) {
            collectSingleItem(item, collectionState, context.messageVariables());
        }

        state.setCollectionState(collectionState);
        datacollStatePort.update(eqpId, correlationId, state);

        log.debug("COLLECT_DCDATA 수집 완료. eqpId={}, workflowName={}, collectedItems={}",
                eqpId, workflowName, matchedItems.size());
    }

    /**
     * 단일 DCOP Item의 값을 수집하고 collectionState를 갱신합니다.
     *
     * <p>variableId가 null이거나 collectionRule이 null이면 건너뜁니다.
     * variableId에 해당하는 값이 없으면 해당 항목은 건너뜁니다.</p>
     *
     * @param item              DCOP Item 정의
     * @param collectionState   갱신할 collectionState Map
     * @param messageVariables  현재 이벤트 message 변수
     */
    private void collectSingleItem(
            final TcModelDcopItem item,
            final Map<String, ItemCollectionState> collectionState,
            final Map<String, Object> messageVariables
    ) {
        final String dcopItemName = item.dcopItemName();
        final String variableId = item.variableId();
        final DcopCollectionRule rule = item.collectionRule();

        if (variableId == null || rule == null) {
            log.debug("variableId 또는 collectionRule이 누락되어 건너뜁니다. dcopItemName={}", dcopItemName);
            return;
        }

        // variableId로 현재 이벤트 payload의 data 블록에서 값 추출
        final Object rawValue = resolveVariableValue(messageVariables, variableId);
        if (rawValue == null) {
            log.debug("variableId={}에 해당하는 값이 없습니다. dcopItemName={} 건너뜁니다.", variableId, dcopItemName);
            return;
        }

        final String collectedValue = String.valueOf(rawValue);
        updateCollectionState(collectionState, dcopItemName, rule, collectedValue);
    }

    /**
     * Collection Rule에 따라 collectionState를 갱신합니다.
     *
     * <p>MIN / MAX 갱신 시 숫자 변환이 불가하면 현재 값을 그대로 유지합니다.</p>
     *
     * @param collectionState 갱신할 collectionState Map
     * @param dcopItemName    항목명
     * @param rule            Collection Rule
     * @param collectedValue  수집된 값 (String)
     */
    private void updateCollectionState(
            final Map<String, ItemCollectionState> collectionState,
            final String dcopItemName,
            final DcopCollectionRule rule,
            final String collectedValue
    ) {
        final ItemCollectionState current = collectionState.get(dcopItemName);

        switch (rule) {
            case FIRST -> {
                // 최초 수집값만 저장. 이후 실행은 무시.
                if (current == null) {
                    collectionState.put(dcopItemName, ItemCollectionState.ofValue(rule, collectedValue));
                }
            }
            case LAST -> {
                // 매 실행마다 value 덮어쓰기
                collectionState.put(dcopItemName, ItemCollectionState.ofValue(rule, collectedValue));
            }
            case AVERAGE -> {
                final BigDecimal numeric = toBigDecimalSafe(collectedValue);
                if (numeric == null) {
                    // 숫자로 변환 불가한 값은 AVERAGE 누적에서 제외
                    log.debug("AVERAGE 규칙: 숫자 변환 실패로 건너뜁니다. dcopItemName={}, value={}",
                            dcopItemName, collectedValue);
                    return;
                }
                if (current == null) {
                    collectionState.put(dcopItemName, ItemCollectionState.ofAverageFirst(numeric));
                } else {
                    collectionState.put(dcopItemName, current.accumulateAverage(numeric));
                }
            }
            case MIN -> {
                if (current == null) {
                    collectionState.put(dcopItemName, ItemCollectionState.ofValue(rule, collectedValue));
                } else {
                    final BigDecimal currentVal = toBigDecimalSafe(current.value());
                    final BigDecimal newVal = toBigDecimalSafe(collectedValue);
                    // 숫자 비교 가능한 경우에만 갱신. 비교 불가 시 현재 값 유지.
                    if (currentVal != null && newVal != null && newVal.compareTo(currentVal) < 0) {
                        collectionState.put(dcopItemName, ItemCollectionState.ofValue(rule, collectedValue));
                    }
                }
            }
            case MAX -> {
                if (current == null) {
                    collectionState.put(dcopItemName, ItemCollectionState.ofValue(rule, collectedValue));
                } else {
                    final BigDecimal currentVal = toBigDecimalSafe(current.value());
                    final BigDecimal newVal = toBigDecimalSafe(collectedValue);
                    // 숫자 비교 가능한 경우에만 갱신. 비교 불가 시 현재 값 유지.
                    if (currentVal != null && newVal != null && newVal.compareTo(currentVal) > 0) {
                        collectionState.put(dcopItemName, ItemCollectionState.ofValue(rule, collectedValue));
                    }
                }
            }
        }
    }

    /**
     * messageVariables의 data 블록에서 variableId 경로의 값을 추출합니다.
     *
     * <p>variableId는 단순 key 또는 dot-notation 경로일 수 있습니다.
     * 예: {@code "Temperature"} 또는 {@code "sensor.Temperature"}</p>
     *
     * @param messageVariables 메시지 변수 맵
     * @param variableId       조회할 변수 ID (data 블록 내 경로)
     * @return 조회된 값. 없으면 null
     */
    @SuppressWarnings("unchecked")
    private static Object resolveVariableValue(
            final Map<String, Object> messageVariables,
            final String variableId
    ) {
        if (messageVariables == null || variableId == null) {
            return null;
        }

        // data 블록에서 variableId 경로로 조회
        final Object dataBlock = messageVariables.get("data");
        if (!(dataBlock instanceof Map<?, ?> dataMap)) {
            return null;
        }

        return lookupPath((Map<String, Object>) dataMap, variableId);
    }

    /**
     * dot-path로 중첩 Map 값을 조회합니다.
     *
     * <p>먼저 전체 path를 key로 직접 조회하고, 없으면 dot으로 분할하여 탐색합니다.
     * 이는 "sensor.temp"처럼 점이 포함된 단순 key를 우선 처리하기 위함입니다.</p>
     *
     * @param source 조회 대상 Map
     * @param path   dot-notation 경로
     * @return 조회된 값. 없으면 null
     */
    @SuppressWarnings("unchecked")
    private static Object lookupPath(final Map<String, Object> source, final String path) {
        if (source == null || path == null || path.isBlank()) {
            return null;
        }
        // 전체 path를 단순 key로 먼저 시도
        if (source.containsKey(path)) {
            return source.get(path);
        }
        // dot-path로 분할하여 순차 탐색
        Object current = source;
        for (final String part : path.split("\\.")) {
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
     * 문자열을 BigDecimal로 안전하게 변환합니다.
     *
     * @param value 변환할 문자열
     * @return 변환된 BigDecimal. null이거나 변환 불가 시 null
     */
    private static BigDecimal toBigDecimalSafe(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
