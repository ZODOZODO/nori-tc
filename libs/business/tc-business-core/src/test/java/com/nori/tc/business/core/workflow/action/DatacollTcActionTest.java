package com.nori.tc.business.core.workflow.action;

import com.nori.tc.business.core.datacoll.DatacollStatePort;
import com.nori.tc.business.core.datacoll.DcopCollectionEngine;
import com.nori.tc.business.core.datacoll.DcopItemPort;
import com.nori.tc.business.core.messaging.BusinessMesCommandMessage;
import com.nori.tc.business.core.messaging.BusinessMesCommandPublishPort;
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
 * {@link DatacollTcAction} 단위 테스트입니다.
 *
 * <p>외부 Redis/Kafka 없이 스텁을 사용하여 DATACOLL 처리 흐름을 검증합니다.</p>
 */
@DisplayName("DatacollTcAction 단위 테스트")
class DatacollTcActionTest {

    private StubDatacollStatePort stubStatePort;
    private StubDcopItemPort stubDcopItemPort;
    private StubMesCommandPublishPort stubPublishPort;
    private DatacollTcAction action;

    @BeforeEach
    void setUp() {
        stubStatePort = new StubDatacollStatePort();
        stubDcopItemPort = new StubDcopItemPort();
        stubPublishPort = new StubMesCommandPublishPort();
        // DcopCollectionEngine은 외부 의존성이 없으므로 실제 인스턴스 사용
        action = new DatacollTcAction(stubStatePort, new DcopCollectionEngine(), stubDcopItemPort, stubPublishPort);
    }

    // -------------------------------------------------------------------------
    // DatacollState 없음
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DatacollState가 없으면 WARN 후 정상 종료 (Kafka 미발행)")
    void reportDatacoll_whenStateNotFound_shouldSkip() throws Exception {
        // given
        stubStatePort.existingState = null;
        final BusinessWorkflowActionContext context = createContext("EQP-001", "CORR-001", Map.of());

        // when
        action.reportDatacoll(context);

        // then
        assertFalse(stubPublishPort.published, "DatacollState 없으면 Kafka 발행이 일어나선 안 됩니다");
    }

    @Test
    @DisplayName("correlationId가 없으면 조기 종료 (Kafka 미발행)")
    void reportDatacoll_whenCorrelationIdNull_shouldSkip() throws Exception {
        // given
        stubStatePort.existingState = DatacollState.initFrom(Map.of("Temperature", ""));
        final BusinessWorkflowActionContext context = createContextWithoutCorrelationId("EQP-001", Map.of());

        // when
        action.reportDatacoll(context);

        // then
        assertFalse(stubPublishPort.published, "correlationId 없으면 Kafka 발행이 일어나선 안 됩니다");
    }

    // -------------------------------------------------------------------------
    // 정상 발행
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DATACOLL action 실행 후 MES로 Kafka 메시지 발행")
    void reportDatacoll_shouldPublishKafkaMessage() throws Exception {
        // given
        final DatacollState state = DatacollState.initFrom(Map.of("Temperature", ""));
        state.getCollectionState().put(
                "Temperature", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "23.5"));
        stubStatePort.existingState = state;

        stubDcopItemPort.items = List.of(
                dcopItem("Temperature", "WORKFLOW_A", "Temperature", DcopCollectionRule.LAST, null, 0)
        );

        final BusinessWorkflowActionContext context = createContext("EQP-001", "CORR-001", Map.of());

        // when
        action.reportDatacoll(context);

        // then
        assertTrue(stubPublishPort.published, "Kafka 발행이 호출되어야 합니다");
        final BusinessMesCommandMessage msg = stubPublishPort.lastMessage;
        assertNotNull(msg, "발행된 메시지가 있어야 합니다");
        assertEquals("DATACOLL", msg.eventType());
        assertEquals("CORR-001", msg.correlationId());
        assertEquals("EQP-001", msg.eqpId());
    }

    @Test
    @DisplayName("dcspecValue에 수집 결과가 올바르게 채워져야 함")
    @SuppressWarnings("unchecked")
    void reportDatacoll_shouldFillDcspecValue() throws Exception {
        // given
        final DatacollState state = DatacollState.initFrom(
                Map.of("Temperature", "", "Humidity", "", "Pressure", ""));
        state.getCollectionState().put(
                "Temperature", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "23.5"));
        state.getCollectionState().put(
                "Humidity", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "60"));
        // Pressure는 collectionState에 없음 → "" 처리
        stubStatePort.existingState = state;

        stubDcopItemPort.items = List.of(
                dcopItem("Temperature", "WORKFLOW_A", "Temperature", DcopCollectionRule.LAST, null, 0),
                dcopItem("Humidity", "WORKFLOW_A", "Humidity", DcopCollectionRule.LAST, null, 1)
        );

        final BusinessWorkflowActionContext context = createContext("EQP-001", "CORR-001", Map.of());

        // when
        action.reportDatacoll(context);

        // then
        final Map<String, String> dcspecValue = (Map<String, String>) stubPublishPort.lastMessage.data().get("dcspecValue");
        assertEquals("23.5", dcspecValue.get("Temperature"), "Temperature 최종값이 채워져야 합니다");
        assertEquals("60", dcspecValue.get("Humidity"), "Humidity 최종값이 채워져야 합니다");
        assertEquals("", dcspecValue.get("Pressure"), "collectionState 없는 Pressure는 빈 문자열이어야 합니다");
    }

    @Test
    @DisplayName("dcspecValue key에 매핑 없는 항목은 빈 문자열로 처리")
    @SuppressWarnings("unchecked")
    void reportDatacoll_unmappedDcspecKey_shouldBeEmpty() throws Exception {
        // given - dcspecValue에 "UnknownItem"이 있으나 DCOP Item 정의 없음
        final DatacollState state = DatacollState.initFrom(
                Map.of("Temperature", "", "UnknownItem", ""));
        state.getCollectionState().put(
                "Temperature", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "23.5"));
        stubStatePort.existingState = state;

        // DCOP Item 목록에 UnknownItem이 없음
        stubDcopItemPort.items = List.of(
                dcopItem("Temperature", "WORKFLOW_A", "Temperature", DcopCollectionRule.LAST, null, 0)
        );

        final BusinessWorkflowActionContext context = createContext("EQP-001", "CORR-001", Map.of());

        // when
        action.reportDatacoll(context);

        // then
        final Map<String, String> dcspecValue = (Map<String, String>) stubPublishPort.lastMessage.data().get("dcspecValue");
        assertEquals("23.5", dcspecValue.get("Temperature"));
        assertEquals("", dcspecValue.get("UnknownItem"), "매핑 없는 항목은 빈 문자열이어야 합니다");
    }

    @Test
    @DisplayName("AVERAGE Rule: sum/count 최종값이 올바르게 채워져야 함")
    @SuppressWarnings("unchecked")
    void reportDatacoll_averageRule_shouldComputeAverage() throws Exception {
        // given - count=3, sum=117.0 → 평균 = 39.0
        final DatacollState state = DatacollState.initFrom(Map.of("Pressure", ""));
        state.getCollectionState().put(
                "Pressure", new ItemCollectionState(DcopCollectionRule.AVERAGE, 3L, new BigDecimal("117.0"), null));
        stubStatePort.existingState = state;

        stubDcopItemPort.items = List.of(
                dcopItem("Pressure", "WORKFLOW_A", "Pressure", DcopCollectionRule.AVERAGE, null, 0)
        );

        final BusinessWorkflowActionContext context = createContext("EQP-001", "CORR-001", Map.of());

        // when
        action.reportDatacoll(context);

        // then
        final Map<String, String> dcspecValue = (Map<String, String>) stubPublishPort.lastMessage.data().get("dcspecValue");
        assertEquals("39", dcspecValue.get("Pressure"), "AVERAGE: sum/count 평균값이 채워져야 합니다");
    }

    // -------------------------------------------------------------------------
    // Redis 삭제
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DATACOLL 발행 완료 후 Redis key가 삭제되어야 함")
    void reportDatacoll_afterPublish_shouldDeleteRedisKey() throws Exception {
        // given
        final DatacollState state = DatacollState.initFrom(Map.of("Temperature", ""));
        state.getCollectionState().put(
                "Temperature", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "23.5"));
        stubStatePort.existingState = state;
        stubDcopItemPort.items = List.of(
                dcopItem("Temperature", "WORKFLOW_A", "Temperature", DcopCollectionRule.LAST, null, 0)
        );

        final BusinessWorkflowActionContext context = createContext("EQP-001", "CORR-001", Map.of());

        // when
        action.reportDatacoll(context);

        // then
        assertTrue(stubStatePort.deleted, "발행 완료 후 Redis key가 삭제되어야 합니다");
        assertEquals("EQP-001", stubStatePort.deletedEqpId);
        assertEquals("CORR-001", stubStatePort.deletedCorrelationId);
    }

    @Test
    @DisplayName("Redis 삭제 실패 시에도 Kafka 발행은 완료된 상태 유지")
    void reportDatacoll_whenDeleteFails_shouldKeepPublishedState() throws Exception {
        // given
        final DatacollState state = DatacollState.initFrom(Map.of("Temperature", ""));
        state.getCollectionState().put(
                "Temperature", ItemCollectionState.ofValue(DcopCollectionRule.LAST, "23.5"));
        stubStatePort.existingState = state;
        // Redis 삭제 시 예외 발생하도록 설정
        stubStatePort.throwOnDelete = true;
        stubDcopItemPort.items = List.of(
                dcopItem("Temperature", "WORKFLOW_A", "Temperature", DcopCollectionRule.LAST, null, 0)
        );

        final BusinessWorkflowActionContext context = createContext("EQP-001", "CORR-001", Map.of());

        // when - 예외가 전파되어선 안 됨
        assertDoesNotThrow(() -> action.reportDatacoll(context));

        // then - Kafka 발행은 이미 완료된 상태
        assertTrue(stubPublishPort.published, "Redis 삭제 실패 시에도 Kafka 발행은 완료되어야 합니다");
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
            final Map<String, Object> extraData
    ) {
        final BusinessInboundRecord record = new BusinessInboundRecord(
                "tc.mes.events", 0, 1L, eqpId,
                "TRACE-01", BusinessMessageType.MES,
                "SOME_EVENT", "payload://mes/1", null
        );

        final Map<String, Object> vars = new LinkedHashMap<>(extraData);
        vars.put("metadata", Map.of("correlationId", correlationId));

        return buildContext(record, Map.copyOf(vars));
    }

    /**
     * correlationId가 없는 테스트 컨텍스트를 생성합니다.
     */
    private BusinessWorkflowActionContext createContextWithoutCorrelationId(
            final String eqpId,
            final Map<String, Object> extraData
    ) {
        final BusinessInboundRecord record = new BusinessInboundRecord(
                "tc.mes.events", 0, 1L, eqpId,
                "TRACE-01", BusinessMessageType.MES,
                "SOME_EVENT", "payload://mes/1", null
        );
        return buildContext(record, Map.copyOf(extraData));
    }

    /**
     * 공통 컨텍스트 조립 헬퍼입니다.
     */
    private BusinessWorkflowActionContext buildContext(
            final BusinessInboundRecord record,
            final Map<String, Object> messageVariables
    ) {
        final TcModel model = new TcModel(
                100L, 100L, "TEST-MODEL", null, "v1",
                ProtocolType.SOCKET, ModelStatus.OPERATE,
                null, "NORI",
                OffsetDateTime.now(), OffsetDateTime.now(),
                "SYSTEM", "SYSTEM"
        );

        final TcModelRuntime runtime = TcModelRuntime.from(
                model, List.of(), List.of(), List.of(), List.of(),
                MdfRuntimeDefinition.empty()
        );

        final WorkflowRuntimeEntry workflowEntry = new WorkflowRuntimeEntry(
                1L, "WORKFLOW_A", "SOME_EVENT",
                null, null, null,
                "DATACOLL", null, 0
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

        DatacollState existingState;
        boolean deleted = false;
        String deletedEqpId;
        String deletedCorrelationId;
        boolean throwOnDelete = false;

        @Override
        public void save(final String eqpId, final String correlationId, final DatacollState state) {
        }

        @Override
        public Optional<DatacollState> findByKey(final String eqpId, final String correlationId) {
            if (existingState == null) {
                return Optional.empty();
            }
            // 깊은 복사로 Map 공유 방지
            final Map<String, String> dcspecValue = new HashMap<>(existingState.getDcspecValue());
            final Map<String, ItemCollectionState> collectionState = new HashMap<>(existingState.getCollectionState());
            return Optional.of(new DatacollState(dcspecValue, collectionState));
        }

        @Override
        public void update(final String eqpId, final String correlationId, final DatacollState state) {
        }

        @Override
        public void delete(final String eqpId, final String correlationId) {
            if (throwOnDelete) {
                throw new RuntimeException("Redis 삭제 실패 (테스트용 예외)");
            }
            this.deleted = true;
            this.deletedEqpId = eqpId;
            this.deletedCorrelationId = correlationId;
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

    /**
     * BusinessMesCommandPublishPort 인메모리 스텁입니다.
     */
    static class StubMesCommandPublishPort implements BusinessMesCommandPublishPort {

        boolean published = false;
        BusinessMesCommandMessage lastMessage;

        @Override
        public void publish(final BusinessMesCommandMessage command) {
            this.published = true;
            this.lastMessage = command;
        }
    }
}
