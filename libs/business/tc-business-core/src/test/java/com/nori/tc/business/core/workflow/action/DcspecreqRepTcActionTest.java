package com.nori.tc.business.core.workflow.action;

import com.nori.tc.business.core.datacoll.DatacollStatePort;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionMessageType;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterContext;
import com.nori.tc.business.domain.datacoll.DatacollState;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.runtime.BusinessMessageType;
import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.model.TcModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DcspecreqRepTcAction} 단위 테스트입니다.
 *
 * <p>외부 Redis 없이 {@link StubDatacollStatePort}를 사용하여 액션 동작을 검증합니다.</p>
 */
@DisplayName("DcspecreqRepTcAction 단위 테스트")
class DcspecreqRepTcActionTest {

    private StubDatacollStatePort stubPort;
    private DcspecreqRepTcAction action;

    @BeforeEach
    void setUp() {
        stubPort = new StubDatacollStatePort();
        action = new DcspecreqRepTcAction(stubPort);
    }

    // -------------------------------------------------------------------------
    // 정상 흐름
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DCSPECREQ_REP 처리 후 DatacollState가 Redis에 저장된다")
    void handleDcspecreqRep_shouldSaveDatacollStateToRedis() {
        // given
        final Map<String, String> dcspecValue = new LinkedHashMap<>();
        dcspecValue.put("Temperature", "");
        dcspecValue.put("Humidity", "");
        dcspecValue.put("Pressure", "");

        final BusinessWorkflowActionContext context = createContext(
                "PHOTO_002",
                "L-0121010",
                dcspecValue
        );

        // when
        action.handleDcspecreqRep(context);

        // then
        assertTrue(stubPort.saved, "DatacollState가 저장되어야 합니다");
        assertEquals("PHOTO_002", stubPort.savedEqpId);
        assertEquals("L-0121010", stubPort.savedCorrelationId);
    }

    @Test
    @DisplayName("dcspecValue key 목록이 올바르게 초기화되어 저장된다")
    void handleDcspecreqRep_shouldInitializeDcspecValueKeys() {
        // given
        final Map<String, String> dcspecValue = new LinkedHashMap<>();
        dcspecValue.put("Temperature", "");
        dcspecValue.put("Humidity", "");

        final BusinessWorkflowActionContext context = createContext(
                "EQP-001", "CORR-001", dcspecValue
        );

        // when
        action.handleDcspecreqRep(context);

        // then
        assertNotNull(stubPort.savedState);
        // 저장된 dcspecValue에 동일한 key가 있어야 합니다
        assertTrue(stubPort.savedState.getDcspecValue().containsKey("Temperature"),
                "Temperature key가 저장되어야 합니다");
        assertTrue(stubPort.savedState.getDcspecValue().containsKey("Humidity"),
                "Humidity key가 저장되어야 합니다");
        // 모든 value는 "" 로 초기화
        assertEquals("", stubPort.savedState.getDcspecValue().get("Temperature"),
                "dcspecValue의 초기 value는 빈 문자열이어야 합니다");
        // collectionState는 빈 Map
        assertTrue(stubPort.savedState.getCollectionState().isEmpty(),
                "초기 collectionState는 빈 Map이어야 합니다");
    }

    @Test
    @DisplayName("이미 동일 key가 Redis에 존재하면 덮어쓰기(save 재호출)가 실행된다")
    void handleDcspecreqRep_whenKeyAlreadyExists_shouldOverwrite() {
        // given - 이미 동일 key의 DatacollState 존재
        final DatacollState existing = DatacollState.initFrom(Map.of("OldKey", ""));
        stubPort.existingState = existing;

        final Map<String, String> dcspecValue = new LinkedHashMap<>();
        dcspecValue.put("Temperature", "");

        final BusinessWorkflowActionContext context = createContext(
                "EQP-001", "CORR-001", dcspecValue
        );

        // when
        action.handleDcspecreqRep(context);

        // then - save가 호출되어 덮어쓰기가 실행되어야 합니다
        assertTrue(stubPort.saved, "덮어쓰기를 위해 save가 호출되어야 합니다");
        assertTrue(stubPort.savedState.getDcspecValue().containsKey("Temperature"),
                "새 dcspecValue key가 저장되어야 합니다");
        assertFalse(stubPort.savedState.getDcspecValue().containsKey("OldKey"),
                "이전 key는 사라지고 새 key로 교체되어야 합니다");
    }

    // -------------------------------------------------------------------------
    // correlationId 누락
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("correlationId가 null이면 저장하지 않고 조기 종료한다")
    void handleDcspecreqRep_whenCorrelationIdIsNull_shouldNotSave() {
        // given - correlationId 없는 컨텍스트
        final BusinessWorkflowActionContext context = createContextWithoutCorrelationId(
                "EQP-001",
                Map.of("Temperature", "")
        );

        // when
        action.handleDcspecreqRep(context);

        // then
        assertFalse(stubPort.saved, "correlationId 없으면 저장하지 않아야 합니다");
    }

    // -------------------------------------------------------------------------
    // dcspecValue 경로 누락
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("messageVariables에 data 블록이 없으면 dcspecValue가 null로 처리되어 빈 DatacollState가 저장된다")
    void handleDcspecreqRep_whenDataBlockMissing_shouldSaveEmptyState() {
        // given - data 블록 없는 컨텍스트
        final BusinessWorkflowActionContext context = createContextWithoutDataBlock(
                "EQP-001", "CORR-001"
        );

        // when
        action.handleDcspecreqRep(context);

        // then - dcspecValue가 null이어도 저장은 수행 (warn 로그 + 빈 상태 저장)
        assertTrue(stubPort.saved, "data 블록 없어도 save는 호출되어야 합니다");
        assertTrue(stubPort.savedState.getDcspecValue().isEmpty(),
                "dcspecValue가 null이면 빈 Map으로 저장되어야 합니다");
    }

    // -------------------------------------------------------------------------
    // 테스트 헬퍼
    // -------------------------------------------------------------------------

    /**
     * 정상 케이스용 테스트 컨텍스트를 생성합니다.
     * correlationId는 metadata.correlationId 경로에 포함됩니다.
     *
     * @param eqpId         설비 ID
     * @param correlationId lot 식별자
     * @param dcspecValue   수집 항목 목록
     * @return 테스트용 컨텍스트
     */
    private BusinessWorkflowActionContext createContext(
            final String eqpId,
            final String correlationId,
            final Map<String, String> dcspecValue
    ) {
        final BusinessInboundRecord record = new BusinessInboundRecord(
                "tc.mes.events",
                0,
                1L,
                eqpId,
                "TRACE-01",
                BusinessMessageType.MES,
                "DCSPECREQ_REP",
                "payload://mes/1",
                null
        );

        final Map<String, Object> messageVariables = new LinkedHashMap<>();
        // correlationId는 metadata.correlationId 경로에 포함
        messageVariables.put("metadata", Map.of(
                "eventType", "DCSPECREQ_REP",
                "correlationId", correlationId
        ));
        // dcspecValue는 data.dcspecValue 경로에 포함
        final Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("eqpId", eqpId);
        dataMap.put("dcspecValue", new LinkedHashMap<>(dcspecValue));
        messageVariables.put("data", dataMap);

        return buildContext(record, messageVariables);
    }

    /**
     * correlationId가 없는 테스트 컨텍스트를 생성합니다.
     *
     * @param eqpId       설비 ID
     * @param dcspecValue 수집 항목 목록
     * @return 테스트용 컨텍스트
     */
    private BusinessWorkflowActionContext createContextWithoutCorrelationId(
            final String eqpId,
            final Map<String, String> dcspecValue
    ) {
        final BusinessInboundRecord record = new BusinessInboundRecord(
                "tc.mes.events",
                0,
                1L,
                eqpId,
                "TRACE-01",
                BusinessMessageType.MES,
                "DCSPECREQ_REP",
                "payload://mes/1",
                null
        );

        // metadata 자체가 없어 correlationId 조회 실패
        final Map<String, Object> messageVariables = new LinkedHashMap<>();
        final Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("eqpId", eqpId);
        dataMap.put("dcspecValue", new LinkedHashMap<>(dcspecValue));
        messageVariables.put("data", dataMap);

        return buildContext(record, messageVariables);
    }

    /**
     * data 블록이 없는 테스트 컨텍스트를 생성합니다.
     *
     * @param eqpId         설비 ID
     * @param correlationId lot 식별자
     * @return 테스트용 컨텍스트
     */
    private BusinessWorkflowActionContext createContextWithoutDataBlock(
            final String eqpId,
            final String correlationId
    ) {
        final BusinessInboundRecord record = new BusinessInboundRecord(
                "tc.mes.events",
                0,
                1L,
                eqpId,
                "TRACE-01",
                BusinessMessageType.MES,
                "DCSPECREQ_REP",
                "payload://mes/1",
                null
        );

        final Map<String, Object> messageVariables = new LinkedHashMap<>();
        messageVariables.put("metadata", Map.of(
                "eventType", "DCSPECREQ_REP",
                "correlationId", correlationId
        ));
        // data 블록 없음

        return buildContext(record, messageVariables);
    }

    /**
     * 공통 컨텍스트 조립 헬퍼입니다.
     *
     * @param record           인바운드 레코드
     * @param messageVariables 메시지 변수 맵
     * @return 조립된 테스트용 컨텍스트
     */
    private BusinessWorkflowActionContext buildContext(
            final BusinessInboundRecord record,
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
                1L, "DCSPECREQ_WORKFLOW", "DCSPECREQ_REP",
                null, null, null,
                "DCSPECREQ_REP", null, 0
        );

        final BusinessWorkflowFilterContext filterContext = new BusinessWorkflowFilterContext(
                record,
                Map.copyOf(messageVariables),
                Map.of("eqpId", record.eqpId())
        );

        return new BusinessWorkflowActionContext(
                record, runtime, workflowEntry, filterContext,
                BusinessWorkflowActionMessageType.MES
        );
    }

    // -------------------------------------------------------------------------
    // DatacollStatePort 스텁
    // -------------------------------------------------------------------------

    /**
     * 단위 테스트용 인메모리 DatacollStatePort 스텁입니다.
     *
     * <p>저장/조회 동작을 내부 상태 변수로 기록합니다.</p>
     */
    static class StubDatacollStatePort implements DatacollStatePort {

        boolean saved = false;
        String savedEqpId;
        String savedCorrelationId;
        DatacollState savedState;

        /** 미리 주입할 기존 상태. 존재 시 findByKey()에서 반환됩니다. */
        DatacollState existingState = null;

        @Override
        public void save(final String eqpId, final String correlationId, final DatacollState state) {
            this.saved = true;
            this.savedEqpId = eqpId;
            this.savedCorrelationId = correlationId;
            this.savedState = state;
        }

        @Override
        public Optional<DatacollState> findByKey(final String eqpId, final String correlationId) {
            return Optional.ofNullable(existingState);
        }

        @Override
        public void update(final String eqpId, final String correlationId, final DatacollState state) {
            // 단위 테스트에서 사용하지 않습니다
        }

        @Override
        public void delete(final String eqpId, final String correlationId) {
            // 단위 테스트에서 사용하지 않습니다
        }
    }
}
