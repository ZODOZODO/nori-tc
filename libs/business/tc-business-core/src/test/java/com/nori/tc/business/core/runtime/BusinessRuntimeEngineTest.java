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
 * {@link BusinessRuntimeEngine} 湲곕낯 ?숈옉 ?뚯뒪?몄엯?덈떎.
 */
class BusinessRuntimeEngineTest {

    /**
     * runtime ?ㅽ뻾 以묒뿉??inbound record媛 ?뺤긽 ?섎씫?섎뒗吏 ?뺤씤?⑸땲??
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
                    "tc.ui.events.business",
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
     * non-UI(EQP/MES) 寃쎈줈?먯꽌 workflow 留ㅼ묶 ??action executor媛 ?몄텧?섎뒗吏 ?뺤씤?⑸땲??
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
     * ?고??꾩씠 ?쒖옉?섏? ?딆? ?곹깭?먯꽌 submit ?몄텧 ??REJECTED disposition???꾩쟻?섎뒗吏 寃利앺빀?덈떎.
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
                "tc.ui.events.business",
                0,
                10L,
                "EQP-TEST-REJECTED",
                BusinessMessageType.UI,
                "EQP_UPDATE",
                "payload://ui/rejected",
                "{\"sample\":false}"
        ));

        Assertions.assertFalse(accepted, "?쒖옉 ??submit? 嫄곕??섏뼱???⑸땲??");
        Assertions.assertEquals(
                1L,
                dispositionMetrics.count(BusinessRuntimeDisposition.REJECTED),
                "?쒖옉 ??submit? REJECTED disposition 1嫄댁쑝濡?吏묎퀎?섏뼱???⑸땲??"
        );
        Assertions.assertEquals(
                1L,
                dispositionMetrics.count("UI_EVENT", BusinessRuntimeDisposition.REJECTED),
                "?쒖옉 ??UI submit? UI_EVENT:REJECTED 1嫄댁쑝濡?吏묎퀎?섏뼱???⑸땲??"
        );
    }

    /**
     * ?뺤긽 泥섎━??non-UI task媛 ACCEPTED disposition?쇰줈 吏묎퀎?섎뒗吏 寃利앺빀?덈떎.
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
            // ?깃났 ?쒕굹由ъ삤 寃利앹쓣 ?꾪빐 no-op ?ㅽ뻾湲곕줈 ?〓땲??
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
            Assertions.assertTrue(accepted, "?고???湲곕룞 以?submit? ?섎씫?섏뼱???⑸땲??");

            awaitUntil(() -> dispositionMetrics.count(BusinessRuntimeDisposition.ACCEPTED) > 0L, 2_000L);
            Assertions.assertEquals(
                    1L,
                    dispositionMetrics.count(BusinessRuntimeDisposition.ACCEPTED),
                    "?뺤긽 泥섎━??task??ACCEPTED disposition 1嫄댁쑝濡?吏묎퀎?섏뼱???⑸땲??"
            );
            Assertions.assertEquals(
                    1L,
                    dispositionMetrics.count("EQP_EVENT", BusinessRuntimeDisposition.ACCEPTED),
                    "EQP ?대깽???뺤긽 泥섎━??EQP_EVENT:ACCEPTED 1嫄댁쑝濡?吏묎퀎?섏뼱???⑸땲??"
            );
        } finally {
            runtimeEngine.stop();
        }
    }

    /**
     * ?쒗븳 ?쒓컙 ?덉뿉 議곌굔??留뚯”???뚭퉴吏 ?湲고빀?덈떎.
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
     * ?뚯뒪?몄슜 TcModelRuntime???앹꽦?⑸땲??
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



