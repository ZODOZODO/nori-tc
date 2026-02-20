package com.nori.tc.business.core.workflow;

import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionExecutionException;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionMessageType;
import com.nori.tc.business.core.workflow.api.annotation.TcAction;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterContext;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowMatchResult;
import com.nori.tc.business.core.workflow.api.plugin.BusinessWorkflowPluginRuntimeProvider;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistry;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistryBuilder;
import com.nori.tc.business.core.workflow.api.spi.executor.AbstractSocketActionExecutor;
import com.nori.tc.business.core.workflow.internal.core.BusinessWorkflowCoreActionRegistry;
import com.nori.tc.business.core.workflow.internal.dispatch.BusinessWorkflowDispatchingActionExecutor;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.runtime.BusinessMessageType;
import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.model.TcModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link BusinessWorkflowDispatchingActionExecutor} ?⑥쐞 ?뚯뒪?몄엯?덈떎.
 */
class BusinessWorkflowDispatchingActionExecutorTest {

    @Test
    void shouldExecuteCoreActionWhenPluginActionIsNotPresent() {
        final AtomicInteger coreExecutionCount = new AtomicInteger(0);
        final AbstractSocketActionExecutor coreSocketExecutor = new AbstractSocketActionExecutor() {
            /**
             * execute 湲곕뒫???섑뻾?⑸땲??
             *
             * @param context ?낅젰 媛?             */

            @TcAction("SOCKET_ACT")
            public void execute(final BusinessWorkflowActionContext context) {
                coreExecutionCount.incrementAndGet();
            }
        };

        final BusinessWorkflowCoreActionRegistry coreRegistry = new BusinessWorkflowCoreActionRegistry(
                List.of(),
                List.of(coreSocketExecutor),
                List.of()
        );
        final BusinessWorkflowDispatchingActionExecutor dispatchingExecutor = new BusinessWorkflowDispatchingActionExecutor(
                coreRegistry,
                BusinessWorkflowPluginRuntimeProvider.noop()
        );

        final BusinessInboundRecord record = createRecord("EQP-CORE-01", "SOCKET_IN");
        dispatchingExecutor.execute(
                record,
                createRuntime(ProtocolType.SOCKET),
                createMatchResult(record, "SOCKET_ACT")
        );

        Assertions.assertEquals(1, coreExecutionCount.get(), "plugin???놁쑝硫?core action??1???ㅽ뻾?섏뼱???⑸땲??");
    }

    @Test
    void shouldPreferPluginActionWhenPluginAndCoreActionsAreBothRegistered() {
        final AtomicInteger coreExecutionCount = new AtomicInteger(0);
        final AtomicInteger pluginExecutionCount = new AtomicInteger(0);

        final AbstractSocketActionExecutor coreSocketExecutor = new AbstractSocketActionExecutor() {
            /**
             * execute 湲곕뒫???섑뻾?⑸땲??
             *
             * @param context ?낅젰 媛?             */

            @TcAction("SOCKET_ACT")
            public void execute(final BusinessWorkflowActionContext context) {
                coreExecutionCount.incrementAndGet();
            }
        };
        final AbstractSocketActionExecutor pluginSocketExecutor = new AbstractSocketActionExecutor() {
            /**
             * execute 湲곕뒫???섑뻾?⑸땲??
             *
             * @param context ?낅젰 媛?             */

            @TcAction("SOCKET_ACT")
            public void execute(final BusinessWorkflowActionContext context) {
                pluginExecutionCount.incrementAndGet();
            }
        };

        final BusinessWorkflowActionRegistry pluginRegistry = new BusinessWorkflowActionRegistryBuilder()
                .registerExecutor(pluginSocketExecutor, BusinessWorkflowActionMessageType.SOCKET)
                .build();

        final BusinessWorkflowCoreActionRegistry coreRegistry = new BusinessWorkflowCoreActionRegistry(
                List.of(),
                List.of(coreSocketExecutor),
                List.of()
        );
        final BusinessWorkflowPluginRuntimeProvider pluginProvider = eqpId -> Optional.of(pluginRegistry);
        final BusinessWorkflowDispatchingActionExecutor dispatchingExecutor = new BusinessWorkflowDispatchingActionExecutor(
                coreRegistry,
                pluginProvider
        );

        final BusinessInboundRecord record = createRecord("EQP-PLUGIN-01", "SOCKET_IN");
        dispatchingExecutor.execute(
                record,
                createRuntime(ProtocolType.SOCKET),
                createMatchResult(record, "SOCKET_ACT")
        );

        Assertions.assertEquals(0, coreExecutionCount.get(), "plugin action???덉쑝硫?core action? ?ㅽ뻾?섎㈃ ???⑸땲??");
        Assertions.assertEquals(1, pluginExecutionCount.get(), "plugin action? 1???ㅽ뻾?섏뼱???⑸땲??");
    }

    @Test
    void shouldFallbackToCoreWhenPluginRegistryExistsButActionIsMissing() {
        final AtomicInteger coreExecutionCount = new AtomicInteger(0);
        final AtomicInteger pluginExecutionCount = new AtomicInteger(0);

        final AbstractSocketActionExecutor coreSocketExecutor = new AbstractSocketActionExecutor() {
            /**
             * execute 湲곕뒫???섑뻾?⑸땲??
             *
             * @param context ?낅젰 媛?             */

            @TcAction("SOCKET_ACT")
            public void execute(final BusinessWorkflowActionContext context) {
                coreExecutionCount.incrementAndGet();
            }
        };
        final AbstractSocketActionExecutor pluginSocketExecutor = new AbstractSocketActionExecutor() {
            /**
             * execute 湲곕뒫???섑뻾?⑸땲??
             *
             * @param context ?낅젰 媛?             */

            @TcAction("PLUGIN_ONLY_ACT")
            public void execute(final BusinessWorkflowActionContext context) {
                pluginExecutionCount.incrementAndGet();
            }
        };

        final BusinessWorkflowActionRegistry pluginRegistry = new BusinessWorkflowActionRegistryBuilder()
                .registerExecutor(pluginSocketExecutor, BusinessWorkflowActionMessageType.SOCKET)
                .build();
        final BusinessWorkflowCoreActionRegistry coreRegistry = new BusinessWorkflowCoreActionRegistry(
                List.of(),
                List.of(coreSocketExecutor),
                List.of()
        );
        final BusinessWorkflowDispatchingActionExecutor dispatchingExecutor = new BusinessWorkflowDispatchingActionExecutor(
                coreRegistry,
                eqpId -> Optional.of(pluginRegistry)
        );

        final BusinessInboundRecord record = createRecord("EQP-FALLBACK-01", "SOCKET_IN");
        dispatchingExecutor.execute(
                record,
                createRuntime(ProtocolType.SOCKET),
                createMatchResult(record, "SOCKET_ACT")
        );

        Assertions.assertEquals(1, coreExecutionCount.get(), "plugin key媛 ?놁쑝硫?core fallback?쇰줈 ?ㅽ뻾?섏뼱???⑸땲??");
        Assertions.assertEquals(0, pluginExecutionCount.get(), "key媛 ?ㅻⅨ plugin action? ?ㅽ뻾?섎㈃ ???⑸땲??");
    }

    @Test
    void shouldFallbackToCoreAfterPluginActionIsDeletedFromRuntime() {
        final AtomicInteger coreExecutionCount = new AtomicInteger(0);
        final AtomicInteger pluginExecutionCount = new AtomicInteger(0);

        final AbstractSocketActionExecutor coreSocketExecutor = new AbstractSocketActionExecutor() {
            /**
             * execute 湲곕뒫???섑뻾?⑸땲??
             *
             * @param context ?낅젰 媛?             */

            @TcAction("SOCKET_ACT")
            public void execute(final BusinessWorkflowActionContext context) {
                coreExecutionCount.incrementAndGet();
            }
        };
        final AbstractSocketActionExecutor pluginSocketExecutor = new AbstractSocketActionExecutor() {
            /**
             * execute 湲곕뒫???섑뻾?⑸땲??
             *
             * @param context ?낅젰 媛?             */

            @TcAction("SOCKET_ACT")
            public void execute(final BusinessWorkflowActionContext context) {
                pluginExecutionCount.incrementAndGet();
            }
        };

        final BusinessWorkflowActionRegistry pluginRegistry = new BusinessWorkflowActionRegistryBuilder()
                .registerExecutor(pluginSocketExecutor, BusinessWorkflowActionMessageType.SOCKET)
                .build();
        final AtomicReference<Optional<BusinessWorkflowActionRegistry>> pluginRuntimeRef =
                new AtomicReference<>(Optional.of(pluginRegistry));

        final BusinessWorkflowCoreActionRegistry coreRegistry = new BusinessWorkflowCoreActionRegistry(
                List.of(),
                List.of(coreSocketExecutor),
                List.of()
        );
        final BusinessWorkflowDispatchingActionExecutor dispatchingExecutor = new BusinessWorkflowDispatchingActionExecutor(
                coreRegistry,
                eqpId -> pluginRuntimeRef.get()
        );

        final BusinessInboundRecord record = createRecord("EQP-DELETE-01", "SOCKET_IN");

        // 1) ?뚮윭洹몄씤 議댁옱 ??plugin ?곗꽑 ?ㅽ뻾
        dispatchingExecutor.execute(
                record,
                createRuntime(ProtocolType.SOCKET),
                createMatchResult(record, "SOCKET_ACT")
        );

        // 2) ?뚮윭洹몄씤 ?고????쒓굅(??젣) ?꾩뿉??core fallback ?ㅽ뻾
        pluginRuntimeRef.set(Optional.empty());
        dispatchingExecutor.execute(
                record,
                createRuntime(ProtocolType.SOCKET),
                createMatchResult(record, "SOCKET_ACT")
        );

        Assertions.assertEquals(1, pluginExecutionCount.get(), "?뚮윭洹몄씤 ??젣 ?꾩뿉??plugin??1???ㅽ뻾?섏뼱???⑸땲??");
        Assertions.assertEquals(1, coreExecutionCount.get(), "?뚮윭洹몄씤 ??젣 ?꾩뿉??core fallback??1???ㅽ뻾?섏뼱???⑸땲??");
    }

    @Test
    void shouldThrowWhenActionHandlerIsNotRegistered() {
        final BusinessWorkflowCoreActionRegistry coreRegistry = new BusinessWorkflowCoreActionRegistry(
                List.of(),
                List.of(),
                List.of()
        );
        final BusinessWorkflowDispatchingActionExecutor dispatchingExecutor = new BusinessWorkflowDispatchingActionExecutor(
                coreRegistry,
                BusinessWorkflowPluginRuntimeProvider.noop()
        );
        final BusinessInboundRecord record = createRecord("EQP-ERR-01", "SOCKET_IN");

        final BusinessWorkflowActionExecutionException exception = Assertions.assertThrows(
                BusinessWorkflowActionExecutionException.class,
                () -> dispatchingExecutor.execute(
                        record,
                        createRuntime(ProtocolType.SOCKET),
                        createMatchResult(record, "UNKNOWN_ACTION")
                )
        );

        Assertions.assertTrue(
                exception.getMessage().contains("resolution="),
                "誘명빐寃??덉쇅?먮뒗 ?댁꽍 trace ?붿빟???ы븿?섏뼱???⑸땲??"
        );
    }

    /**
     * ?뚯뒪?몄슜 inbound record瑜??앹꽦?⑸땲??
     */
    private static BusinessInboundRecord createRecord(final String eqpId, final String messageName) {
        return new BusinessInboundRecord(
                "tc.eqp.events",
                0,
                1L,
                eqpId,
                BusinessMessageType.EQP,
                messageName,
                "payload://test/1",
                "{\"raw\":\"sample\"}"
        );
    }

    /**
     * ?뚯뒪?몄슜 model runtime???앹꽦?⑸땲??
     */
    private static TcModelRuntime createRuntime(final ProtocolType protocolType) {
        final OffsetDateTime now = OffsetDateTime.now();
        final TcModel model = new TcModel(
                300L,
                "MODEL-300",
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
     * ?⑥씪 workflow 留ㅼ묶 寃곌낵瑜??앹꽦?⑸땲??
     */
    private static BusinessWorkflowMatchResult createMatchResult(
            final BusinessInboundRecord record,
            final String actionName
    ) {
        final WorkflowRuntimeEntry entry = new WorkflowRuntimeEntry(
                700L,
                "WF-700",
                record.messageName(),
                null,
                null,
                null,
                actionName,
                null,
                0
        );
        final BusinessWorkflowFilterContext filterContext = new BusinessWorkflowFilterContext(
                record,
                Map.of(),
                Map.of()
        );
        return new BusinessWorkflowMatchResult(List.of(entry), filterContext);
    }
}

