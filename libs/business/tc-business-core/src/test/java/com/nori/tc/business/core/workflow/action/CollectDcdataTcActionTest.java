package com.nori.tc.business.core.workflow.action;

import com.nori.tc.business.core.datacoll.DatacollStatePort;
import com.nori.tc.business.core.datacoll.DcopItemPort;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionMessageType;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterContext;
import com.nori.tc.business.domain.datacoll.DatacollState;
import com.nori.tc.business.domain.datacoll.ItemCollectionState;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.runtime.BusinessMessageType;
import com.nori.tc.db.domain.common.model.DcopCollectionRule;
import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.domain.model.TcModelDcopItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CollectDcdataTcAction} 단위 테스트입니다.
 *
 * <p>외부 Redis/DB 없이 스텁을 사용하여 Collection Rule별 동작을 검증합니다.</p>
 */
@DisplayName("CollectDcdataTcAction 단위 테스트")
class CollectDcdataTcActionTest {

    private StubDatacollStatePort stubPort;
    private StubDcopItemPort stubDcopItemPort;
    private CollectDcdataTcAction action;

    @BeforeEach
    void setUp() {
        stubPort = new StubDatacollStatePort();
        stubDcopItemPort = new StubDcopItemPort();
        action = new CollectDcdataTcAction(stubPort, stubDcopItemPort);
    }

    // -------------------------------------------------------------------------
    // DatacollState 없음
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DatacollState가 없으면 warn 로그 후 정상 종료 (update 미호출)")
    void collectDcdata_whenStateNotFound_shouldSkip() {
        // given - Redis에 DatacollState 없음
        stubPort.existingState = null;

        final BusinessWorkflowActionContext context = createContext(
                "EQP-001", "CORR-001", "WORKFLOW_A", Map.of("data", Map.of("Temperature", "23.5")));

        // when
        action.collectDcdata(context);

        // then
        assertFalse(stubPort.updated, "DatacollState 없으면 update가 호출되어서는 안 됩니다");
    }

    @Test
    @DisplayName("correlationId가 없으면 조기 종료 (update 미호출)")
    void collectDcdata_whenCorrelationIdNull_shouldSkip() {
        // given
        stubPort.existingState = DatacollState.initFrom(Map.of("Temperature", ""));

        final BusinessWorkflowActionContext context = createContextWithoutCorrelationId(
                "EQP-001", "WORKFLOW_A", Map.of("data", Map.of("Temperature", "23.5")));

        // when
        action.collectDcdata(context);

        // then
        assertFalse(stubPort.updated, "correlationId 없으면 update가 호출되어서는 안 됩니다");
    }

    // -------------------------------------------------------------------------
    // FIRST Rule
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("FIRST Rule: 첫 번째 COLLECT_DCDATA 실행 시 값 저장")
    void collectDcdata_firstRule_shouldSaveFirstValue() {
        // given
        stubPort.existingState = DatacollState.initFrom(Map.of("Temperature", ""));
        stubDcopItemPort.items = List.of(
                dcopItem("Temperature", "WORKFLOW_A", "Temperature", DcopCollectionRule.FIRST, null, 0)
        );

        final BusinessWorkflowActionContext context = createContext(
                "EQP-001", "CORR-001", "WORKFLOW_A", Map.of("data", Map.of("Temperature", "23.5")));

        // when
        action.collectDcdata(context);

        // then
        assertTrue(stubPort.updated, "collectionState 갱신 후 update가 호출되어야 합니다");
        final ItemCollectionState cs = stubPort.updatedState.getCollectionState().get("Temperature");
        assertNotNull(cs, "collectionState에 Temperature가 저장되어야 합니다");
        assertEquals("23.5", cs.value(), "FIRST Rule: 첫 번째 값이 저장되어야 합니다");
    }

    @Test
    @DisplayName("FIRST Rule: 두 번째 COLLECT_DCDATA 실행 시 첫 번째 값 유지")
    void collectDcdata_firstRule_shouldIgnoreSecondValue() {
        // given - 이미 FIRST 값이 저장된 상태
        final DatacollState existingState = DatacollState.initFrom(Map.of("Temperature", ""));
        existingState.getCollectionState().put(
                "Temperature", ItemCollectionState.ofValue(DcopCollectionRule.FIRST, "23.5"));
        stubPort.existingState = existingState;
        stubDcopItemPort.items = List.of(
                dcopItem("Temperature", "WORKFLOW_A", "Temperature", DcopCollectionRule.FIRST, null, 0)
        );

        final BusinessWorkflowActionContext context = createContext(
                "EQP-001", "CORR-001", "WORKFLOW_A", Map.of("data", Map.of("Temperature", "99.9")));

        // when
        action.collectDcdata(context);

        // then
        final ItemCollectionState cs = stubPort.updatedState.getCollectionState().get("Temperature");
        assertEquals("23.5", cs.value(), "FIRST Rule: 두 번째 실행에서 첫 번째 값이 유지되어야 합니다");
    }

    // -------------------------------------------------------------------------
    // LAST Rule
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("LAST Rule: 두 번째 COLLECT_DCDATA 실행 시 마지막 값으로 덮어쓰기")
    void collectDcdata_lastRule_shouldOverwriteWithLastValue() {
        // given - 이미 LAST 값이 저장된 상태
        final DatacollState existingState = DatacollState.initFrom(Map.of("Humidity", ""));
        existingState.getCollectionState().put(
                "Humidity", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "55.0"));
        stubPort.existingState = existingState;
        stubDcopItemPort.items = List.of(
                dcopItem("Humidity", "WORKFLOW_A", "Humidity", DcopCollectionRule.LAST, null, 0)
        );

        final BusinessWorkflowActionContext context = createContext(
                "EQP-001", "CORR-001", "WORKFLOW_A", Map.of("data", Map.of("Humidity", "60.0")));

        // when
        action.collectDcdata(context);

        // then
        final ItemCollectionState cs = stubPort.updatedState.getCollectionState().get("Humidity");
        assertEquals("60.0", cs.value(), "LAST Rule: 두 번째 값으로 덮어써져야 합니다");
    }

    // -------------------------------------------------------------------------
    // AVERAGE Rule
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AVERAGE Rule: 3회 실행 후 count=3, sum=합계 누적")
    void collectDcdata_averageRule_shouldAccumulateCountAndSum() {
        // given
        final DatacollState state = DatacollState.initFrom(Map.of("Pressure", ""));
        stubPort.existingState = state;
        stubDcopItemPort.items = List.of(
                dcopItem("Pressure", "WORKFLOW_A", "Pressure", DcopCollectionRule.AVERAGE, null, 0)
        );

        // 1회 실행: 100
        action.collectDcdata(createContext(
                "EQP-001", "CORR-001", "WORKFLOW_A", Map.of("data", Map.of("Pressure", "100"))));

        // 2회 실행: 200 (stubPort.existingState를 updatedState로 교체)
        stubPort.existingState = stubPort.updatedState;
        action.collectDcdata(createContext(
                "EQP-001", "CORR-001", "WORKFLOW_A", Map.of("data", Map.of("Pressure", "200"))));

        // 3회 실행: 300
        stubPort.existingState = stubPort.updatedState;
        action.collectDcdata(createContext(
                "EQP-001", "CORR-001", "WORKFLOW_A", Map.of("data", Map.of("Pressure", "300"))));

        // then
        final ItemCollectionState cs = stubPort.updatedState.getCollectionState().get("Pressure");
        assertNotNull(cs, "Pressure collectionState가 있어야 합니다");
        assertEquals(3L, cs.count(), "AVERAGE Rule: count=3이어야 합니다");
        assertEquals(new BigDecimal("600"), cs.sum(), "AVERAGE Rule: sum=600이어야 합니다");
    }

    // -------------------------------------------------------------------------
    // MIN Rule
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MIN Rule: 더 작은 값이 오면 갱신")
    void collectDcdata_minRule_shouldUpdateWithSmallerValue() {
        // given - 현재 최솟값 50
        final DatacollState existingState = DatacollState.initFrom(Map.of("Pressure", ""));
        existingState.getCollectionState().put(
                "Pressure", ItemCollectionState.ofValue(DcopCollectionRule.MIN, "50.0"));
        stubPort.existingState = existingState;
        stubDcopItemPort.items = List.of(
                dcopItem("Pressure", "WORKFLOW_A", "Pressure", DcopCollectionRule.MIN, null, 0)
        );

        final BusinessWorkflowActionContext context = createContext(
                "EQP-001", "CORR-001", "WORKFLOW_A", Map.of("data", Map.of("Pressure", "30.0")));

        // when
        action.collectDcdata(context);

        // then
        final ItemCollectionState cs = stubPort.updatedState.getCollectionState().get("Pressure");
        assertEquals("30.0", cs.value(), "MIN Rule: 더 작은 값(30.0)으로 갱신되어야 합니다");
    }

    @Test
    @DisplayName("MIN Rule: 더 큰 값이 오면 유지")
    void collectDcdata_minRule_shouldKeepCurrentWhenNewValueIsLarger() {
        // given - 현재 최솟값 50
        final DatacollState existingState = DatacollState.initFrom(Map.of("Pressure", ""));
        existingState.getCollectionState().put(
                "Pressure", ItemCollectionState.ofValue(DcopCollectionRule.MIN, "50.0"));
        stubPort.existingState = existingState;
        stubDcopItemPort.items = List.of(
                dcopItem("Pressure", "WORKFLOW_A", "Pressure", DcopCollectionRule.MIN, null, 0)
        );

        final BusinessWorkflowActionContext context = createContext(
                "EQP-001", "CORR-001", "WORKFLOW_A", Map.of("data", Map.of("Pressure", "80.0")));

        // when
        action.collectDcdata(context);

        // then
        final ItemCollectionState cs = stubPort.updatedState.getCollectionState().get("Pressure");
        assertEquals("50.0", cs.value(), "MIN Rule: 더 큰 값(80.0)이 오면 현재 최솟값(50.0)이 유지되어야 합니다");
    }

    // -------------------------------------------------------------------------
    // MAX Rule
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MAX Rule: 더 큰 값이 오면 갱신")
    void collectDcdata_maxRule_shouldUpdateWithLargerValue() {
        // given - 현재 최댓값 50
        final DatacollState existingState = DatacollState.initFrom(Map.of("Temperature", ""));
        existingState.getCollectionState().put(
                "Temperature", ItemCollectionState.ofValue(DcopCollectionRule.MAX, "50.0"));
        stubPort.existingState = existingState;
        stubDcopItemPort.items = List.of(
                dcopItem("Temperature", "WORKFLOW_A", "Temperature", DcopCollectionRule.MAX, null, 0)
        );

        final BusinessWorkflowActionContext context = createContext(
                "EQP-001", "CORR-001", "WORKFLOW_A", Map.of("data", Map.of("Temperature", "75.0")));

        // when
        action.collectDcdata(context);

        // then
        final ItemCollectionState cs = stubPort.updatedState.getCollectionState().get("Temperature");
        assertEquals("75.0", cs.value(), "MAX Rule: 더 큰 값(75.0)으로 갱신되어야 합니다");
    }

    @Test
    @DisplayName("MAX Rule: 더 작은 값이 오면 유지")
    void collectDcdata_maxRule_shouldKeepCurrentWhenNewValueIsSmaller() {
        // given - 현재 최댓값 50
        final DatacollState existingState = DatacollState.initFrom(Map.of("Temperature", ""));
        existingState.getCollectionState().put(
                "Temperature", ItemCollectionState.ofValue(DcopCollectionRule.MAX, "50.0"));
        stubPort.existingState = existingState;
        stubDcopItemPort.items = List.of(
                dcopItem("Temperature", "WORKFLOW_A", "Temperature", DcopCollectionRule.MAX, null, 0)
        );

        final BusinessWorkflowActionContext context = createContext(
                "EQP-001", "CORR-001", "WORKFLOW_A", Map.of("data", Map.of("Temperature", "20.0")));

        // when
        action.collectDcdata(context);

        // then
        final ItemCollectionState cs = stubPort.updatedState.getCollectionState().get("Temperature");
        assertEquals("50.0", cs.value(), "MAX Rule: 더 작은 값(20.0)이 오면 현재 최댓값(50.0)이 유지되어야 합니다");
    }

    // -------------------------------------------------------------------------
    // workflowName 필터링
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("다른 workflowName의 DCOP Item은 수집하지 않는다")
    void collectDcdata_shouldOnlyCollectItemsMatchingWorkflowName() {
        // given
        stubPort.existingState = DatacollState.initFrom(Map.of("Temperature", "", "Humidity", ""));
        stubDcopItemPort.items = List.of(
                dcopItem("Temperature", "WORKFLOW_A", "Temperature", DcopCollectionRule.LAST, null, 0),
                // 다른 workflowName - 수집 대상 아님
                dcopItem("Humidity", "WORKFLOW_B", "Humidity", DcopCollectionRule.LAST, null, 0)
        );

        final BusinessWorkflowActionContext context = createContext(
                "EQP-001", "CORR-001", "WORKFLOW_A",
                Map.of("data", Map.of("Temperature", "23.5", "Humidity", "60.0")));

        // when
        action.collectDcdata(context);

        // then
        final Map<String, ItemCollectionState> cs = stubPort.updatedState.getCollectionState();
        assertNotNull(cs.get("Temperature"), "WORKFLOW_A의 Temperature는 수집되어야 합니다");
        assertNull(cs.get("Humidity"), "다른 workflowName의 Humidity는 수집되어서는 안 됩니다");
    }

    // -------------------------------------------------------------------------
    // variableId 없음
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("variableId에 해당하는 값이 없으면 해당 항목 건너뜀")
    void collectDcdata_whenVariableIdValueMissing_shouldSkipItem() {
        // given
        stubPort.existingState = DatacollState.initFrom(Map.of("Temperature", ""));
        stubDcopItemPort.items = List.of(
                dcopItem("Temperature", "WORKFLOW_A", "NonExistentVar", DcopCollectionRule.LAST, null, 0)
        );

        final BusinessWorkflowActionContext context = createContext(
                "EQP-001", "CORR-001", "WORKFLOW_A",
                Map.of("data", Map.of("Temperature", "23.5")));

        // when
        action.collectDcdata(context);

        // then - update는 호출되나 collectionState에 항목 없음
        final Map<String, ItemCollectionState> cs = stubPort.updatedState.getCollectionState();
        assertNull(cs.get("Temperature"), "variableId 값 없으면 collectionState에 추가되지 않아야 합니다");
    }

    // -------------------------------------------------------------------------
    // 테스트 헬퍼
    // -------------------------------------------------------------------------

    /**
     * TcModelDcopItem 테스트 픽스처를 생성합니다.
     */
    private static TcModelDcopItem dcopItem(
            final String dcopItemName,
            final String workflowName,
            final String variableId,
            final DcopCollectionRule rule,
            final String calculationRule,
            final int orderRule
    ) {
        return new TcModelDcopItem(
                1L, 100L, dcopItemName, workflowName,
                null, variableId, rule, calculationRule, orderRule,
                OffsetDateTime.now()
        );
    }

    /**
     * 정상 케이스용 테스트 컨텍스트를 생성합니다.
     */
    private BusinessWorkflowActionContext createContext(
            final String eqpId,
            final String correlationId,
            final String workflowName,
            final Map<String, Object> messageVariables
    ) {
        final BusinessInboundRecord record = new BusinessInboundRecord(
                "tc.mes.events", 0, 1L, eqpId,
                "TRACE-01", BusinessMessageType.MES,
                "SOME_EVENT", "payload://mes/1", null
        );

        final Map<String, Object> vars = new LinkedHashMap<>(messageVariables);
        vars.put("metadata", Map.of("correlationId", correlationId));

        return buildContext(record, workflowName, Map.copyOf(vars));
    }

    /**
     * correlationId가 없는 테스트 컨텍스트를 생성합니다.
     */
    private BusinessWorkflowActionContext createContextWithoutCorrelationId(
            final String eqpId,
            final String workflowName,
            final Map<String, Object> messageVariables
    ) {
        final BusinessInboundRecord record = new BusinessInboundRecord(
                "tc.mes.events", 0, 1L, eqpId,
                "TRACE-01", BusinessMessageType.MES,
                "SOME_EVENT", "payload://mes/1", null
        );
        return buildContext(record, workflowName, Map.copyOf(messageVariables));
    }

    /**
     * 공통 컨텍스트 조립 헬퍼입니다.
     */
    private BusinessWorkflowActionContext buildContext(
            final BusinessInboundRecord record,
            final String workflowName,
            final Map<String, Object> messageVariables
    ) {
        final TcModel model = new TcModel(
                100L, 100L, "TEST-MODEL", null, "v1",
                ProtocolType.SECS, ModelStatus.OPERATE,
                null, "NORI",
                OffsetDateTime.now(), OffsetDateTime.now(),
                "SYSTEM", "SYSTEM"
        );

        final TcModelRuntime runtime = TcModelRuntime.from(
                model, List.of(), List.of(), List.of(), List.of(),
                MdfRuntimeDefinition.empty()
        );

        final WorkflowRuntimeEntry workflowEntry = new WorkflowRuntimeEntry(
                1L, workflowName, "SOME_EVENT",
                null, null, null,
                "COLLECT_DCDATA", null, 0
        );

        final BusinessWorkflowFilterContext filterContext = new BusinessWorkflowFilterContext(
                record,
                messageVariables,
                Map.of("eqpId", record.eqpId())
        );

        return new BusinessWorkflowActionContext(
                record, runtime, workflowEntry, filterContext,
                BusinessWorkflowActionMessageType.MES
        );
    }

    // -------------------------------------------------------------------------
    // 스텁
    // -------------------------------------------------------------------------

    /**
     * DatacollStatePort 인메모리 스텁입니다.
     */
    static class StubDatacollStatePort implements DatacollStatePort {

        boolean updated = false;
        DatacollState updatedState;
        DatacollState existingState;

        @Override
        public void save(final String eqpId, final String correlationId, final DatacollState state) {
        }

        @Override
        public Optional<DatacollState> findByKey(final String eqpId, final String correlationId) {
            if (existingState == null) {
                return Optional.empty();
            }
            // 테스트에서 여러 번 실행할 때 deep copy 반환 (Map 공유 방지)
            return Optional.of(deepCopy(existingState));
        }

        @Override
        public void update(final String eqpId, final String correlationId, final DatacollState state) {
            this.updated = true;
            this.updatedState = state;
            // 다음 findByKey 호출 시 갱신된 상태를 반환할 수 있도록 existingState도 업데이트
            this.existingState = state;
        }

        @Override
        public void delete(final String eqpId, final String correlationId) {
        }

        /**
         * DatacollState를 깊은 복사하여 Map 공유를 방지합니다.
         */
        private DatacollState deepCopy(final DatacollState source) {
            final Map<String, String> dcspecValue = new HashMap<>(source.getDcspecValue());
            final Map<String, ItemCollectionState> collectionState = new HashMap<>(source.getCollectionState());
            return new DatacollState(dcspecValue, collectionState);
        }
    }

    /**
     * DcopItemPort 인메모리 스텁입니다.
     */
    static class StubDcopItemPort implements DcopItemPort {

        List<TcModelDcopItem> items = List.of();

        @Override
        public List<TcModelDcopItem> findAllByModelVersionKey(final long modelVersionKey) {
            return items;
        }
    }
}
