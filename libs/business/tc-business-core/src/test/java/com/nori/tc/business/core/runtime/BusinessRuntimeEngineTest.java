package com.nori.tc.business.core.runtime;

import com.nori.tc.business.core.config.BusinessCoreRuntimeProperties;
import com.nori.tc.business.core.modelcache.BusinessModelRuntimeProvider;
import com.nori.tc.business.domain.modelcache.BusinessModelRuntimeSnapshot;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.runtime.BusinessMessageType;
import com.nori.tc.business.core.ui.BusinessUiTaskExecutor;
import com.nori.tc.business.core.workflow.BusinessWorkflowActionExecutor;
import com.nori.tc.business.core.workflow.BusinessWorkflowFilterContext;
import com.nori.tc.business.core.workflow.BusinessWorkflowMatchResult;
import com.nori.tc.business.core.workflow.BusinessWorkflowMatcher;
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
 * {@link BusinessRuntimeEngine} 기본 동작 테스트입니다.
 */
class BusinessRuntimeEngineTest {

    /**
     * runtime 실행 중에는 inbound record가 정상 수락되는지 확인합니다.
     */
    @Test
    void shouldAcceptInboundRecordWhenRuntimeIsRunning() throws Exception {
        final BusinessCoreRuntimeProperties properties = new BusinessCoreRuntimeProperties();
        properties.validate();

        final BusinessRuntimeEngine runtimeEngine = new BusinessRuntimeEngine(properties);
        runtimeEngine.start();
        try {
            Assertions.assertTrue(runtimeEngine.isRunning(), "runtime engine must be running");

            final boolean accepted = runtimeEngine.submit(new BusinessInboundRecord(
                    "tc.ui.events",
                    0,
                    0L,
                    "EQP-TEST-01",
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
     * non-UI(EQP/MES) 경로에서 workflow 매칭 후 action executor가 호출되는지 확인합니다.
     */
    @Test
    void shouldExecuteNonUiActionWhenWorkflowIsMatched() throws Exception {
        final BusinessCoreRuntimeProperties properties = new BusinessCoreRuntimeProperties();
        properties.validate();

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
     * 런타임이 시작되지 않은 상태에서 submit 호출 시 REJECTED disposition이 누적되는지 검증합니다.
     */
    @Test
    void shouldRecordRejectedDispositionWhenSubmitIsCalledBeforeStart() {
        final BusinessCoreRuntimeProperties properties = new BusinessCoreRuntimeProperties();
        properties.validate();

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
                "tc.ui.events",
                0,
                10L,
                "EQP-TEST-REJECTED",
                BusinessMessageType.UI,
                "EQP_UPDATE",
                "payload://ui/rejected",
                "{\"sample\":false}"
        ));

        Assertions.assertFalse(accepted, "시작 전 submit은 거부되어야 합니다.");
        Assertions.assertEquals(
                1L,
                dispositionMetrics.count(BusinessRuntimeDisposition.REJECTED),
                "시작 전 submit은 REJECTED disposition 1건으로 집계되어야 합니다."
        );
        Assertions.assertEquals(
                1L,
                dispositionMetrics.count("UI_EVENT", BusinessRuntimeDisposition.REJECTED),
                "시작 전 UI submit은 UI_EVENT:REJECTED 1건으로 집계되어야 합니다."
        );
    }

    /**
     * 정상 처리된 non-UI task가 ACCEPTED disposition으로 집계되는지 검증합니다.
     */
    @Test
    void shouldRecordAcceptedDispositionWhenTaskIsProcessedSuccessfully() throws Exception {
        final BusinessCoreRuntimeProperties properties = new BusinessCoreRuntimeProperties();
        properties.validate();

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
            // 성공 시나리오 검증을 위해 no-op 실행기로 둡니다.
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
                    BusinessMessageType.EQP,
                    "SOCKET_IN",
                    "payload://eqp/disp/1",
                    "{\"message\":\"PING\"}"
            ));
            Assertions.assertTrue(accepted, "런타임 기동 중 submit은 수락되어야 합니다.");

            awaitUntil(() -> dispositionMetrics.count(BusinessRuntimeDisposition.ACCEPTED) > 0L, 2_000L);
            Assertions.assertEquals(
                    1L,
                    dispositionMetrics.count(BusinessRuntimeDisposition.ACCEPTED),
                    "정상 처리된 task는 ACCEPTED disposition 1건으로 집계되어야 합니다."
            );
            Assertions.assertEquals(
                    1L,
                    dispositionMetrics.count("EQP_EVENT", BusinessRuntimeDisposition.ACCEPTED),
                    "EQP 이벤트 정상 처리는 EQP_EVENT:ACCEPTED 1건으로 집계되어야 합니다."
            );
        } finally {
            runtimeEngine.stop();
        }
    }

    /**
     * 제한 시간 안에 조건이 만족될 때까지 대기합니다.
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
     * 테스트용 TcModelRuntime을 생성합니다.
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
}


