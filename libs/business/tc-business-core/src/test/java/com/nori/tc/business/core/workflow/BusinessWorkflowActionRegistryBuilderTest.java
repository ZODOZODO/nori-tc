package com.nori.tc.business.core.workflow;

import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionMessageType;
import com.nori.tc.business.core.workflow.api.annotation.TcAction;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterContext;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionKey;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionMethodInvoker;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistry;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistryBuilder;
import com.nori.tc.business.core.workflow.api.spi.executor.AbstractSocketActionExecutor;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.runtime.BusinessMessageType;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.model.TcModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link BusinessWorkflowActionRegistryBuilder} ?⑥쐞 ?뚯뒪?몄엯?덈떎.
 */
class BusinessWorkflowActionRegistryBuilderTest {

    @Test
    void shouldRegisterAndInvokeAnnotatedActionMethod() {
        final AtomicInteger counter = new AtomicInteger(0);
        final AbstractSocketActionExecutor executor = new AbstractSocketActionExecutor() {
            /**
             * execute 湲곕뒫???섑뻾?⑸땲??
             *
             * @param context ?낅젰 媛?             */

            @TcAction("SOCKET_TEST_ACTION")
            public void execute(final BusinessWorkflowActionContext context) {
                counter.incrementAndGet();
            }
        };

        final BusinessWorkflowActionRegistry registry = new BusinessWorkflowActionRegistryBuilder()
                .registerExecutor(executor, BusinessWorkflowActionMessageType.SOCKET)
                .build();

        final BusinessWorkflowActionMethodInvoker invoker = registry.find(
                BusinessWorkflowActionKey.of(BusinessWorkflowActionMessageType.SOCKET, "SOCKET_TEST_ACTION")
        ).orElseThrow();
        invoker.invoke(createContext());

        Assertions.assertEquals(1, counter.get());
    }

    @Test
    void shouldThrowWhenDuplicateActionKeyIsRegistered() {
        final AbstractSocketActionExecutor first = new AbstractSocketActionExecutor() {
            /**
             * execute 湲곕뒫???섑뻾?⑸땲??
             *
             * @param context ?낅젰 媛?             */

            @TcAction("DUP_ACTION")
            public void execute(final BusinessWorkflowActionContext context) {
                // no-op
            }
        };
        final AbstractSocketActionExecutor second = new AbstractSocketActionExecutor() {
            /**
             * execute 湲곕뒫???섑뻾?⑸땲??
             *
             * @param context ?낅젰 媛?             */

            @TcAction("DUP_ACTION")
            public void execute(final BusinessWorkflowActionContext context) {
                // no-op
            }
        };

        final BusinessWorkflowActionRegistryBuilder builder = new BusinessWorkflowActionRegistryBuilder()
                .registerExecutor(first, BusinessWorkflowActionMessageType.SOCKET);

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> builder.registerExecutor(second, BusinessWorkflowActionMessageType.SOCKET)
        );
    }

    @Test
    void shouldThrowWhenTcActionMethodSignatureIsInvalid() {
        final AbstractSocketActionExecutor invalidExecutor = new AbstractSocketActionExecutor() {
            /**
             * execute 湲곕뒫???섑뻾?⑸땲??
             *
             * @return 泥섎━ 寃곌낵
             */

            @TcAction("INVALID_SIG")
            public String execute() {
                return "invalid";
            }
        };

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> new BusinessWorkflowActionRegistryBuilder()
                        .registerExecutor(invalidExecutor, BusinessWorkflowActionMessageType.SOCKET)
        );
    }

    /**
     * ?≪뀡 ?몄텧 ?뚯뒪?몄슜 而⑦뀓?ㅽ듃瑜??앹꽦?⑸땲??
     */
    private static BusinessWorkflowActionContext createContext() {
        final BusinessInboundRecord record = new BusinessInboundRecord(
                "tc.eqp.events",
                0,
                1L,
                "EQP-CTX-01",
                BusinessMessageType.EQP,
                "SOCKET_IN",
                "payload://ctx/1",
                "{\"raw\":\"sample\"}"
        );
        final TcModelRuntime runtime = createRuntime();
        final WorkflowRuntimeEntry workflowEntry = new WorkflowRuntimeEntry(
                900L,
                "WF-900",
                "SOCKET_IN",
                null,
                null,
                null,
                "SOCKET_TEST_ACTION",
                null,
                0
        );
        final BusinessWorkflowFilterContext filterContext = new BusinessWorkflowFilterContext(
                record,
                Map.of(),
                Map.of()
        );

        return new BusinessWorkflowActionContext(
                record,
                runtime,
                workflowEntry,
                filterContext,
                BusinessWorkflowActionMessageType.SOCKET
        );
    }

    /**
     * ?뚯뒪?몄슜 model runtime???앹꽦?⑸땲??
     */
    private static TcModelRuntime createRuntime() {
        final OffsetDateTime now = OffsetDateTime.now();
        final TcModel model = new TcModel(
                900L,
                "MODEL-900",
                "v1",
                ProtocolType.SOCKET,
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




