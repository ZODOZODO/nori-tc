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

        // action_data_index.fields에서 "eqpId" 키로 ValueSpec을 조회하므로,
        // variablePath는 fields 맵의 키 이름이어야 합니다.
        final MdfMessageDefinition eqpMessage = new MdfMessageDefinition(
                "TOOL_CONDITION_REQUEST_EQP",
                MdfTargetType.EQP,
                MdfOutputType.RAW_MESSAGE,
                "CMD=TOOL_CONDITION_REQUEST EQPID={EQPID}",
                List.of(new MdfFieldDefinition("EQPID", "eqpId", true))
        );

        // fields에서 "eqpId" 키를 shorthand 경로로 지정: from=data, path=eqpId
        final String actionDataIndex = """
                {
                  "mdfTemplateName": "TOOL_CONDITION_REQUEST_EQP",
                  "fields": {
                    "eqpId": "eqpId"
                  }
                }
                """;

        final BusinessWorkflowActionContext context = createContext(
                new MdfRuntimeDefinition(Map.of(eqpMessage.name(), eqpMessage)),
                "PUBLISH_EQP_COMMAND",
                actionDataIndex,
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

        // KAFKA output은 template이 없으며, kafkaDataBlock()으로 field 값 Map을 반환합니다.
        final MdfMessageDefinition mesMessage = new MdfMessageDefinition(
                "TOOL_CONDITION_REPLY_MES",
                MdfTargetType.MES,
                MdfOutputType.KAFKA,
                null,
                List.of(
                        new MdfFieldDefinition("EQPID", "eqpId", true),
                        new MdfFieldDefinition("STATUS", "status", true)
                )
        );

        // STATUS field는 data.status("ready")를 upper transform으로 "READY"로 변환합니다.
        final String actionDataIndex = """
                {
                  "mdfTemplateName": "TOOL_CONDITION_REPLY_MES",
                  "fields": {
                    "eqpId": "eqpId",
                    "status": { "from": "data", "path": "status", "transforms": ["upper"] }
                  }
                }
                """;

        final BusinessWorkflowActionContext context = createContext(
                new MdfRuntimeDefinition(Map.of(mesMessage.name(), mesMessage)),
                "PUBLISH_MES_COMMAND",
                actionDataIndex,
                "LOT-001"
        );

        executor.publishMesCommand(context);

        final BusinessMesCommandMessage message = published.get();
        Assertions.assertNotNull(message);
        Assertions.assertEquals("LOT-001", message.correlationId());
        // kafkaDataBlock()은 MDF field 이름을 키로, 변환된 값을 value로 반환합니다.
        Assertions.assertEquals("EQP-01", message.data().get("EQPID"));
        Assertions.assertEquals("READY", message.data().get("STATUS"));
    }

    @Test
    void shouldFallbackToRawMessageWhenActionDataIndexIsEmpty() throws Exception {
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
                "CMD=TOOL_CONDITION_REQUEST EQPID={EQPID}",
                List.of(new MdfFieldDefinition("EQPID", "eqpId", true))
        );

        // action_data_index가 null이면 ParsedActionDataIndex.isEmpty() == true → MDF 미적용
        final BusinessWorkflowActionContext context = createContext(
                new MdfRuntimeDefinition(Map.of(eqpMessage.name(), eqpMessage)),
                "PUBLISH_EQP_COMMAND",
                null,
                null
        );

        executor.publishEqpCommand(context);

        final BusinessEqpCommandMessage message = published.get();
        Assertions.assertNotNull(message);
        Assertions.assertEquals("{\"metadata\":{\"eventType\":\"EQP_CONDITION_CHECK\"},\"data\":{\"status\":\"ready\"}}", message.rawMessage());
        Assertions.assertNull(message.attributes().get("mdfMessageName"));
    }

    /**
     * 테스트용 액션 컨텍스트를 생성합니다.
     *
     * @param mdfRuntimeDefinition MDF 런타임 정의
     * @param actionName           액션 이름
     * @param actionDataIndex      action_data_index JSON 문자열 (null이면 MDF 미적용)
     * @param correlationId        correlationId (null이면 messageVariables에 미포함)
     */
    private static BusinessWorkflowActionContext createContext(
            final MdfRuntimeDefinition mdfRuntimeDefinition,
            final String actionName,
            final String actionDataIndex,
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
                "{\"metadata\":{\"eventType\":\"EQP_CONDITION_CHECK\"},\"data\":{\"status\":\"ready\"}}"
        );

        final TcModel model = new TcModel(
                100L,
                100L,
                "MODEL-100",
                null,
                "v1",
                ProtocolType.SECS,
                ModelStatus.OPERATE,
                null,
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

        // data 블록에 eqpId를 포함합니다.
        // MdfFieldDefinition.variablePath("eqpId")로 action_data_index.fields에서 조회한 ValueSpec이
        // from=DATA, path=eqpId로 설정된 경우 messageVariables["data"]["eqpId"]에서 값을 가져옵니다.
        final Map<String, Object> messageVariables = new LinkedHashMap<>();
        messageVariables.put("metadata", Map.of("eventType", "EQP_CONDITION_CHECK"));
        messageVariables.put("data", Map.of("status", "ready", "eqpId", "EQP-01"));
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
