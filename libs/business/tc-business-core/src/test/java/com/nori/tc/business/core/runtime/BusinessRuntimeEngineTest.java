package com.nori.tc.business.core.runtime;

import com.nori.tc.business.core.config.BusinessCoreRuntimeProperties;
import com.nori.tc.business.core.modelcache.BusinessModelRuntimeProvider;
import com.nori.tc.business.domain.modelcache.BusinessModelRuntimeSnapshot;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.runtime.BusinessMessageType;
import com.nori.tc.business.core.ui.BusinessUiTaskExecutor;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionExecutor;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterContext;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowMatchResult;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowMatcher;
import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.model.TcModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * {@link BusinessRuntimeEngine} 동작을 검증하는 단위 테스트입니다.
 */
class BusinessRuntimeEngineTest {

    /**
     * 런타임이 시작된 상태에서는 inbound record가 정상적으로 수락되는지 검증합니다.
     */
    @Test
    void shouldAcceptInboundRecordWhenRuntimeIsRunning() throws Exception {
        final BusinessCoreRuntimeProperties properties = createValidRuntimePropertiesForTest();

        final BusinessRuntimeEngine runtimeEngine = new BusinessRuntimeEngine(properties);
        runtimeEngine.start();
        try {
            Assertions.assertTrue(runtimeEngine.isRunning(), "runtime engine must be running");

            final boolean accepted = runtimeEngine.submit(new BusinessInboundRecord(
                    "tc.ui.events.business",
                    0,
                    0L,
                    "EQP-TEST-01",
                    "TRACE-TEST-01",
                    BusinessMessageType.UI,
                    "EQP_UPDATE",
                    "payload://ui/0",
                    "{\"sample\":true}"
            ));

            Assertions.assertTrue(accepted, "inbound record must be accepted");
            Thread.sleep(150L);
        } finally {
            runtimeEngine.stop();
        }
    }

    /**
     * non-UI(EQP/MES) 메시지에서 워크플로가 매칭되면 action executor가 호출되는지 검증합니다.
     */
    @Test
    void shouldExecuteNonUiActionWhenWorkflowIsMatched() throws Exception {
        final BusinessCoreRuntimeProperties properties = createValidRuntimePropertiesForTest();

        final TcModelRuntime modelRuntime = createRuntime(900L, ProtocolType.SOCKET);
        final BusinessModelRuntimeProvider runtimeProvider = () -> BusinessModelRuntimeSnapshot.of(
                Map.of("EQP-TEST-02", 900L),
                Map.of(900L, modelRuntime)
        );

        final WorkflowRuntimeEntry matchedWorkflow = new WorkflowRuntimeEntry(
                501L,
                "WF-SOCKET-IN",
                "SOCKET_IN",
                null,
                null,
                null,
                "ACT-LOG",
                null,
                0
        );

        final BusinessWorkflowMatcher workflowMatcher = (record, runtime) -> new BusinessWorkflowMatchResult(
                List.of(matchedWorkflow),
                new BusinessWorkflowFilterContext(record, Map.of(), Map.of())
        );

        final AtomicInteger actionExecutedCount = new AtomicInteger(0);
        final BusinessWorkflowActionExecutor actionExecutor = (record, runtime, matchResult) ->
                actionExecutedCount.incrementAndGet();

        final BusinessRuntimeEngine runtimeEngine = new BusinessRuntimeEngine(
                properties,
                runtimeProvider,
                BusinessUiTaskExecutor.noop(),
                workflowMatcher,
                actionExecutor
        );

        runtimeEngine.start();
        try {
            final boolean accepted = runtimeEngine.submit(new BusinessInboundRecord(
                    "tc.eqp.events",
                    0,
                    1L,
                    "EQP-TEST-02",
                    "TRACE-TEST-02",
                    BusinessMessageType.EQP,
                    "SOCKET_IN",
                    "payload://eqp/1",
                    "{\"message\":\"PING\"}"
            ));

            Assertions.assertTrue(accepted, "non-UI inbound record must be accepted");
            awaitUntil(() -> actionExecutedCount.get() > 0, 2_000L);
            Assertions.assertEquals(1, actionExecutedCount.get(), "action executor must be invoked exactly once");
        } finally {
            runtimeEngine.stop();
        }
    }

    /**
     * 런타임 시작 전 submit 호출 시 REJECTED disposition이 기록되는지 검증합니다.
     */
    @Test
    void shouldRecordRejectedDispositionWhenSubmitIsCalledBeforeStart() {
        final BusinessCoreRuntimeProperties properties = createValidRuntimePropertiesForTest();

        final BusinessRuntimeDispositionMetrics dispositionMetrics = new BusinessRuntimeDispositionMetrics();
        final BusinessRuntimeEngine runtimeEngine = new BusinessRuntimeEngine(
                properties,
                BusinessModelRuntimeProvider.noop(),
                BusinessUiTaskExecutor.noop(),
                BusinessWorkflowMatcher.noop(),
                BusinessWorkflowActionExecutor.noop(),
                com.nori.tc.business.core.dlq.BusinessDlqPublisherPort.noop(),
                dispositionMetrics
        );

        final boolean accepted = runtimeEngine.submit(new BusinessInboundRecord(
                "tc.ui.events.business",
                0,
                10L,
                "EQP-TEST-REJECTED",
                "TRACE-TEST-REJECTED",
                BusinessMessageType.UI,
                "EQP_UPDATE",
                "payload://ui/rejected",
                "{\"sample\":false}"
        ));

        Assertions.assertFalse(accepted, "런타임 시작 전 submit은 거부되어야 합니다.");
        Assertions.assertEquals(
                1L,
                dispositionMetrics.count(BusinessRuntimeDisposition.REJECTED),
                "런타임 시작 전 submit은 REJECTED disposition 1건을 기록해야 합니다."
        );
        Assertions.assertEquals(
                1L,
                dispositionMetrics.count("UI_EVENT", BusinessRuntimeDisposition.REJECTED),
                "런타임 시작 전 UI submit은 UI_EVENT:REJECTED 1건을 기록해야 합니다."
        );
    }

    /**
     * 정상 처리된 non-UI task가 ACCEPTED disposition으로 기록되는지 검증합니다.
     */
    @Test
    void shouldRecordAcceptedDispositionWhenTaskIsProcessedSuccessfully() throws Exception {
        final BusinessCoreRuntimeProperties properties = createValidRuntimePropertiesForTest();

        final TcModelRuntime modelRuntime = createRuntime(901L, ProtocolType.SOCKET);
        final BusinessModelRuntimeProvider runtimeProvider = () -> BusinessModelRuntimeSnapshot.of(
                Map.of("EQP-TEST-DISP-01", 901L),
                Map.of(901L, modelRuntime)
        );

        final WorkflowRuntimeEntry matchedWorkflow = new WorkflowRuntimeEntry(
                777L,
                "WF-DISP-OK",
                "SOCKET_IN",
                null,
                null,
                null,
                "ACT-DISP",
                null,
                0
        );
        final BusinessWorkflowMatcher workflowMatcher = (record, runtime) -> new BusinessWorkflowMatchResult(
                List.of(matchedWorkflow),
                new BusinessWorkflowFilterContext(record, Map.of(), Map.of())
        );
        final BusinessWorkflowActionExecutor actionExecutor = (record, runtime, matchResult) -> {
            // 이 테스트는 disposition 집계만 검증하므로 액션 실행 자체는 no-op으로 둡니다.
        };

        final BusinessRuntimeDispositionMetrics dispositionMetrics = new BusinessRuntimeDispositionMetrics();
        final BusinessRuntimeEngine runtimeEngine = new BusinessRuntimeEngine(
                properties,
                runtimeProvider,
                BusinessUiTaskExecutor.noop(),
                workflowMatcher,
                actionExecutor,
                com.nori.tc.business.core.dlq.BusinessDlqPublisherPort.noop(),
                dispositionMetrics
        );

        runtimeEngine.start();
        try {
            final boolean accepted = runtimeEngine.submit(new BusinessInboundRecord(
                    "tc.eqp.events",
                    0,
                    11L,
                    "EQP-TEST-DISP-01",
                    "TRACE-TEST-DISP-01",
                    BusinessMessageType.EQP,
                    "SOCKET_IN",
                    "payload://eqp/disp/1",
                    "{\"message\":\"PING\"}"
            ));
            Assertions.assertTrue(accepted, "정상 실행 경로의 submit은 수락되어야 합니다.");

            awaitUntil(() -> dispositionMetrics.count(BusinessRuntimeDisposition.ACCEPTED) > 0L, 2_000L);
            Assertions.assertEquals(
                    1L,
                    dispositionMetrics.count(BusinessRuntimeDisposition.ACCEPTED),
                    "정상 처리된 task는 ACCEPTED disposition 1건을 기록해야 합니다."
            );
            Assertions.assertEquals(
                    1L,
                    dispositionMetrics.count("EQP_EVENT", BusinessRuntimeDisposition.ACCEPTED),
                    "EQP 메시지 정상 처리 시 EQP_EVENT:ACCEPTED 1건을 기록해야 합니다."
            );
        } finally {
            runtimeEngine.stop();
        }
    }

    /**
     * 조건이 만족될 때까지 짧게 폴링하며 기다리는 테스트 유틸리티입니다.
     */
    private static void awaitUntil(final BooleanSupplier condition, final long timeoutMs) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20L);
        }
        Assertions.fail("condition was not satisfied within timeoutMs=" + timeoutMs);
    }

    /**
     * 테스트용 {@link TcModelRuntime} 인스턴스를 생성합니다.
     */
    private static TcModelRuntime createRuntime(final long modelKey, final ProtocolType protocolType) {
        final OffsetDateTime now = OffsetDateTime.now();
        final TcModel model = new TcModel(
                modelKey,
                "MODEL-" + modelKey,
                "v1",
                protocolType,
                ModelStatus.ACTIVE,
                "NORI",
                now,
                now,
                "SYSTEM",
                "SYSTEM"
        );

        return TcModelRuntime.from(
                model,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    /**
     * {@link BusinessRuntimeEngine} 테스트에서 공통으로 사용하는 유효한 런타임 프로퍼티를 생성합니다.
     *
     * <p>최근 {@link BusinessCoreRuntimeProperties#validate()} 검증 규칙이 강화되어 Kafka 토픽/소스/스레드/큐 관련
     * 필수 값이 모두 채워져야 하므로, 각 테스트에서 중복 설정하지 않고 본 헬퍼에서 일괄 구성합니다.</p>
     *
     * <p>값 선택 원칙:</p>
     * <p>1) 실제 앱 설정과 동일한 토픽 이름을 사용하여 테스트 가독성을 유지합니다.</p>
     * <p>2) 테스트 수행 시간/리소스를 줄이기 위해 스레드/큐 값은 최소 유효값 또는 작은 값으로 설정합니다.</p>
     * <p>3) Kafka consumer thread 수는 현재 검증 규칙상 정확히 1이어야 하므로 1로 고정합니다.</p>
     *
     * @return 검증을 통과하는 {@link BusinessCoreRuntimeProperties}
     */
    private static BusinessCoreRuntimeProperties createValidRuntimePropertiesForTest() {
        final BusinessCoreRuntimeProperties properties = new BusinessCoreRuntimeProperties();

        // Kafka 토픽/메타데이터 설정: validate()에서 필수 텍스트 값으로 검증됩니다.
        properties.getKafka().setEqpEventsTopic("tc.eqp.events");
        properties.getKafka().setMesEventsTopic("tc.mes.events");
        properties.getKafka().setUiEventsTopic("tc.ui.events.business");
        properties.getKafka().setEqpCommandsTopic("tc.eqp.commands");
        properties.getKafka().setMesCommandsTopic("tc.mes.commands");
        properties.getKafka().setUiCommandsTopic("tc.ui.commands");
        properties.getKafka().setSource("TC-BUSINESS-CORE-TEST");

        // 현재 BusinessCoreRuntimeProperties.Kafka.validate() 규칙상 consumer thread 수는 정확히 1이어야 합니다.
        properties.getKafka().setEqpEventsConsumerThreads(1);
        properties.getKafka().setMesEventsConsumerThreads(1);
        properties.getKafka().setUiEventsConsumerThreads(1);

        // Runtime 실행 파라미터 설정: 테스트 목적상 작은 값으로 두되 validate() 조건(>0 / >=0)을 만족시킵니다.
        properties.getRuntime().setDispatcherThreads(1);
        properties.getRuntime().setWorkerThreads(2);
        properties.getRuntime().setTimeoutSchedulerThreads(1);
        properties.getRuntime().setTopicQueueCapacity(64);
        properties.getRuntime().setMailboxCapacity(64);
        properties.getRuntime().setAckDrainMaxBatch(16);
        properties.getRuntime().setTaskTimeoutMs(5_000L);
        properties.getRuntime().setRetryMaxAttempts(1);
        properties.getRuntime().setRetryBackoffMs(0L);

        // 테스트 시작 전에 설정 계약을 명시적으로 검증하여, 누락 시 실패 원인이 즉시 드러나도록 합니다.
        properties.validate();
        return properties;
    }
}
