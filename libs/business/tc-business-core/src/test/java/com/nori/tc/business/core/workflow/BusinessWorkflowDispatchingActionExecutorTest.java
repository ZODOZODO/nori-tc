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
 * {@link BusinessWorkflowDispatchingActionExecutor} 단위 테스트입니다.
 */
class BusinessWorkflowDispatchingActionExecutorTest {

    @Test
    void shouldExecuteCoreActionWhenPluginActionIsNotPresent() {
        final AtomicInteger coreExecutionCount = new AtomicInteger(0);
        final AbstractSocketActionExecutor coreSocketExecutor = new AbstractSocketActionExecutor() {
            /**
             * execute 기능을 수행합니다.
             *
             * @param context 입력 값             */

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

        Assertions.assertEquals(1, coreExecutionCount.get(), "plugin이 없으면 core action이 1회 실행되어야 합니다.");
    }

    @Test
    void shouldPreferPluginActionWhenPluginAndCoreActionsAreBothRegistered() {
        final AtomicInteger coreExecutionCount = new AtomicInteger(0);
        final AtomicInteger pluginExecutionCount = new AtomicInteger(0);

        final AbstractSocketActionExecutor coreSocketExecutor = new AbstractSocketActionExecutor() {
            /**
             * execute 기능을 수행합니다.
             *
             * @param context 입력 값             */

            @TcAction("SOCKET_ACT")
            public void execute(final BusinessWorkflowActionContext context) {
                coreExecutionCount.incrementAndGet();
            }
        };
        final AbstractSocketActionExecutor pluginSocketExecutor = new AbstractSocketActionExecutor() {
            /**
             * execute 기능을 수행합니다.
             *
             * @param context 입력 값             */

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

        Assertions.assertEquals(0, coreExecutionCount.get(), "plugin action이 있으면 core action은 실행되면 안 됩니다.");
        Assertions.assertEquals(1, pluginExecutionCount.get(), "plugin action은 1회 실행되어야 합니다.");
    }

    @Test
    void shouldFallbackToCoreWhenPluginRegistryExistsButActionIsMissing() {
        final AtomicInteger coreExecutionCount = new AtomicInteger(0);
        final AtomicInteger pluginExecutionCount = new AtomicInteger(0);

        final AbstractSocketActionExecutor coreSocketExecutor = new AbstractSocketActionExecutor() {
            /**
             * execute 기능을 수행합니다.
             *
             * @param context 입력 값             */

            @TcAction("SOCKET_ACT")
            public void execute(final BusinessWorkflowActionContext context) {
                coreExecutionCount.incrementAndGet();
            }
        };
        final AbstractSocketActionExecutor pluginSocketExecutor = new AbstractSocketActionExecutor() {
            /**
             * execute 기능을 수행합니다.
             *
             * @param context 입력 값             */

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

        Assertions.assertEquals(1, coreExecutionCount.get(), "plugin key가 없으면 core fallback으로 실행되어야 합니다.");
        Assertions.assertEquals(0, pluginExecutionCount.get(), "key가 다른 plugin action은 실행되면 안 됩니다.");
    }

    @Test
    void shouldFallbackToCoreAfterPluginActionIsDeletedFromRuntime() {
        final AtomicInteger coreExecutionCount = new AtomicInteger(0);
        final AtomicInteger pluginExecutionCount = new AtomicInteger(0);

        final AbstractSocketActionExecutor coreSocketExecutor = new AbstractSocketActionExecutor() {
            /**
             * execute 기능을 수행합니다.
             *
             * @param context 입력 값             */

            @TcAction("SOCKET_ACT")
            public void execute(final BusinessWorkflowActionContext context) {
                coreExecutionCount.incrementAndGet();
            }
        };
        final AbstractSocketActionExecutor pluginSocketExecutor = new AbstractSocketActionExecutor() {
            /**
             * execute 기능을 수행합니다.
             *
             * @param context 입력 값             */

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

        // 1) 플러그인 존재 시 plugin 우선 실행
        dispatchingExecutor.execute(
                record,
                createRuntime(ProtocolType.SOCKET),
                createMatchResult(record, "SOCKET_ACT")
        );

        // 2) 플러그인 런타임 제거(삭제) 후에는 core fallback 실행
        pluginRuntimeRef.set(Optional.empty());
        dispatchingExecutor.execute(
                record,
                createRuntime(ProtocolType.SOCKET),
                createMatchResult(record, "SOCKET_ACT")
        );

        Assertions.assertEquals(1, pluginExecutionCount.get(), "플러그인 삭제 전에는 plugin이 1회 실행되어야 합니다.");
        Assertions.assertEquals(1, coreExecutionCount.get(), "플러그인 삭제 후에는 core fallback이 1회 실행되어야 합니다.");
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
                "미해결 예외에는 해석 trace 요약이 포함되어야 합니다."
        );
    }

    /**
     * 테스트용 inbound record를 생성합니다.
     */
    private static BusinessInboundRecord createRecord(final String eqpId, final String messageName) {
        return new BusinessInboundRecord(
                "tc.eqp.events",
                0,
                1L,
                eqpId,
                "TRACE-DISPATCH-TEST",
                BusinessMessageType.EQP,
                messageName,
                "payload://test/1",
                "{\"raw\":\"sample\"}"
        );
    }

    /**
     * 테스트용 model runtime을 생성합니다.
     */
    private static TcModelRuntime createRuntime(final ProtocolType protocolType) {
        final OffsetDateTime now = OffsetDateTime.now();
        final TcModel model = new TcModel(
                300L,
                300L,
                "MODEL-300",
                null,
                "v1",
                protocolType,
                ModelStatus.OPERATE,
                null,
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
     * 단일 workflow 매칭 결과를 생성합니다.
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
