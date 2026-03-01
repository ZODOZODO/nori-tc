package com.nori.tc.business.core.workflow.internal.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.messaging.BusinessEqpCommandMessage;
import com.nori.tc.business.core.messaging.BusinessMesCommandMessage;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionMessageType;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterContext;
import com.nori.tc.business.core.workflow.internal.support.BusinessActionDataIndexHybridResolver;
import com.nori.tc.business.core.workflow.internal.support.BusinessMdfMessageComposer;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfFieldDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfMessageDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfOutputType;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfSourceType;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfTargetType;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MDF 기반 액션 실행기 통합 동작 테스트입니다.
 */
class BusinessCoreActionExecutorMdfIntegrationTest {

    @Test
    void shouldPublishEqpCommandWithMdfRawMessage() throws Exception {
        final AtomicReference<BusinessEqpCommandMessage> published = new AtomicReference<>();
        final BusinessMdfMessageComposer composer = new BusinessMdfMessageComposer(
                new BusinessActionDataIndexHybridResolver(new ObjectMapper())
        );
        final BusinessCoreSocketActionExecutor executor = new BusinessCoreSocketActionExecutor(
                published::set,
                composer
        );

        final MdfMessageDefinition eqpMessage = new MdfMessageDefinition(
                "TOOL_CONDITION_REQUEST_EQP",
                MdfTargetType.EQP,
                MdfOutputType.RAW_MESSAGE,
                "PUBLISH_EQP_COMMAND",
                "CMD=TOOL_CONDITION_REQUEST EQPID={EQPID}",
                List.of(new MdfFieldDefinition("EQPID", "eqpId", MdfSourceType.CTX, List.of(), null, true))
        );

        final BusinessWorkflowActionContext context = createContext(
                new MdfRuntimeDefinition(Map.of(eqpMessage.name(), eqpMessage)),
                "PUBLISH_EQP_COMMAND",
                "TOOL_CONDITION_REQUEST_EQP",
                null
        );

        executor.publishEqpCommand(context);

        final BusinessEqpCommandMessage message = published.get();
        Assertions.assertNotNull(message);
        Assertions.assertEquals("CMD=TOOL_CONDITION_REQUEST EQPID=EQP-01", message.rawMessage());
        Assertions.assertEquals("EQP-01", message.eqpId());
        Assertions.assertEquals("SOCKET", message.interfaceType());
        Assertions.assertEquals("TOOL_CONDITION_REQUEST_EQP", message.attributes().get("mdfMessageName"));
    }

    @Test
    void shouldPublishMesCommandWithMdfData() throws Exception {
        final AtomicReference<BusinessMesCommandMessage> published = new AtomicReference<>();
        final BusinessMdfMessageComposer composer = new BusinessMdfMessageComposer(
                new BusinessActionDataIndexHybridResolver(new ObjectMapper())
        );
        final BusinessCoreMesActionExecutor executor = new BusinessCoreMesActionExecutor(
                published::set,
                composer
        );

        final MdfMessageDefinition mesMessage = new MdfMessageDefinition(
                "TOOL_CONDITION_REPLY_MES",
                MdfTargetType.MES,
                MdfOutputType.DATA,
                "PUBLISH_MES_COMMAND",
                "EQPID={EQPID} STATUS={STATUS}",
                List.of(
                        new MdfFieldDefinition("EQPID", "eqpId", MdfSourceType.CTX, List.of(), null, true),
                        new MdfFieldDefinition("STATUS", "data.status", MdfSourceType.MSG, List.of("upper"), null, true)
                )
        );

        final BusinessWorkflowActionContext context = createContext(
                new MdfRuntimeDefinition(Map.of(mesMessage.name(), mesMessage)),
                "PUBLISH_MES_COMMAND",
                "TOOL_CONDITION_REPLY_MES",
                "LOT-001"
        );

        executor.publishMesCommand(context);

        final BusinessMesCommandMessage message = published.get();
        Assertions.assertNotNull(message);
        Assertions.assertEquals("LOT-001", message.correlationId());
        Assertions.assertEquals("TOOL_CONDITION_REPLY_MES", message.data().get("mdfMessageName"));
        Assertions.assertEquals("EQPID=EQP-01 STATUS=READY", message.data().get("mdfRenderedMessage"));
        Assertions.assertEquals("EQP-01", message.data().get("EQPID"));
        Assertions.assertEquals("READY", message.data().get("STATUS"));
    }

    /**
     * 테스트용 액션 컨텍스트를 생성합니다.
     */
    private static BusinessWorkflowActionContext createContext(
            final MdfRuntimeDefinition mdfRuntimeDefinition,
            final String actionName,
            final String mdfMessageName,
            final String correlationId
    ) {
        final BusinessInboundRecord record = new BusinessInboundRecord(
                "tc.eqp.events",
                0,
                1L,
                "EQP-01",
                "TRACE-01",
                BusinessMessageType.EQP,
                "S6F11",
                "payload://eqp/1",
                "{\"data\":{\"status\":\"ready\"}}"
        );

        final TcModel model = new TcModel(
                100L,
                100L,
                "MODEL-100",
                "v1",
                ProtocolType.HSMS,
                ModelStatus.ACTIVE,
                "NORI",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "SYSTEM",
                "SYSTEM"
        );

        final TcModelRuntime runtime = TcModelRuntime.from(
                model,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                mdfRuntimeDefinition
        );

        final String actionDataIndex = mdfMessageName == null ? null : mdfMessageName;
        final WorkflowRuntimeEntry workflowEntry = new WorkflowRuntimeEntry(
                1L,
                "WF-1",
                "S6F11",
                null,
                null,
                null,
                actionName,
                actionDataIndex,
                0
        );

        final Map<String, Object> messageVariables = new LinkedHashMap<>();
        messageVariables.put("data", Map.of("status", "ready"));
        if (correlationId != null) {
            messageVariables.put("correlationId", correlationId);
        }

        final BusinessWorkflowFilterContext filterContext = new BusinessWorkflowFilterContext(
                record,
                Map.copyOf(messageVariables),
                Map.of("eqpId", "EQP-01")
        );

        final BusinessWorkflowActionMessageType messageType = "PUBLISH_MES_COMMAND".equals(actionName)
                ? BusinessWorkflowActionMessageType.MES
                : BusinessWorkflowActionMessageType.SECS;

        return new BusinessWorkflowActionContext(
                record,
                runtime,
                workflowEntry,
                filterContext,
                messageType
        );
    }
}
