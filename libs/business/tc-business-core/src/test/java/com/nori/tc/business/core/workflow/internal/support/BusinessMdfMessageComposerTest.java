package com.nori.tc.business.core.workflow.internal.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionMessageType;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterContext;
import com.nori.tc.business.core.workflow.internal.support.BusinessMdfMessageComposer.MdfComposeResult;
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
import java.util.List;
import java.util.Map;

/**
 * {@link BusinessMdfMessageComposer} 단위 테스트입니다.
 */
class BusinessMdfMessageComposerTest {

    private final BusinessMdfMessageComposer composer =
            new BusinessMdfMessageComposer(new BusinessActionDataIndexHybridResolver(new ObjectMapper()));

    @Test
    void shouldComposeRawMessageForEqp() {
        // MDF: EQP 장비로 전송할 RAW_MESSAGE
        final MdfMessageDefinition messageDefinition = new MdfMessageDefinition(
                "TOOL_CONDITION_REQUEST",
                MdfTargetType.EQP,
                MdfOutputType.RAW_MESSAGE,
                "CMD=TOOL_CONDITION_REQUEST EQPID={EQPID} CARID={CARID}",
                List.of(
                        new MdfFieldDefinition("EQPID", "EQPID", true),
                        new MdfFieldDefinition("CARID", "CARID", false)
                )
        );

        // action_data_index.fields key = var 이름 (EQPID, CARID)
        final BusinessWorkflowActionContext context = createContext(
                new MdfRuntimeDefinition(Map.of(messageDefinition.name(), messageDefinition)),
                """
                        {
                          "mdfTemplateName": "TOOL_CONDITION_REQUEST",
                          "fields": {
                            "EQPID": { "from": "data", "path": "eqpId" },
                            "CARID": { "from": "data", "path": "carId" }
                          }
                        }
                        """
        );

        final MdfComposeResult result = composer.compose(context, MdfTargetType.EQP).orElseThrow();

        Assertions.assertEquals("CMD=TOOL_CONDITION_REQUEST EQPID=EQP-01 CARID=CAR-01", result.rawMessage());
        Assertions.assertEquals("EQP-01", result.fieldValues().get("EQPID"));
        Assertions.assertEquals("CAR-01", result.fieldValues().get("CARID"));
    }

    @Test
    void shouldComposeKafkaDataBlockForMes() {
        // MDF: MES로 전송할 KAFKA 메시지 (template 없음)
        final MdfMessageDefinition messageDefinition = new MdfMessageDefinition(
                "TOOL_CONDITION_REPLY",
                MdfTargetType.MES,
                MdfOutputType.KAFKA,
                null,
                List.of(
                        new MdfFieldDefinition("EQPID", "EQPID", true),
                        new MdfFieldDefinition("STATUS", "STATUS", true),
                        new MdfFieldDefinition("CARID", "CARID", false)
                )
        );

        // action_data_index.fields key = var 이름 (EQPID, STATUS, CARID)
        final BusinessWorkflowActionContext context = createContext(
                new MdfRuntimeDefinition(Map.of(messageDefinition.name(), messageDefinition)),
                """
                        {
                          "mdfTemplateName": "TOOL_CONDITION_REPLY",
                          "fields": {
                            "EQPID": { "from": "data", "path": "eqpId" },
                            "STATUS": { "from": "data", "path": "status" }
                          }
                        }
                        """
        );

        final MdfComposeResult result = composer.compose(context, MdfTargetType.MES).orElseThrow();

        final Map<String, Object> dataBlock = result.kafkaDataBlock();
        Assertions.assertEquals("EQP-01", dataBlock.get("EQPID"));
        Assertions.assertEquals("READY", dataBlock.get("STATUS"));
        // optional field CARID는 action_data_index에 없으면 빈 문자열
        Assertions.assertEquals("", dataBlock.get("CARID"));
    }

    @Test
    void shouldReturnEmptyWhenActionDataIndexIsNull() {
        final MdfMessageDefinition messageDefinition = new MdfMessageDefinition(
                "TOOL_CONDITION_REQUEST",
                MdfTargetType.EQP,
                MdfOutputType.RAW_MESSAGE,
                "CMD=REQ EQPID={EQPID}",
                List.of(new MdfFieldDefinition("EQPID", "EQPID", false))
        );

        final BusinessWorkflowActionContext context = createContext(
                new MdfRuntimeDefinition(Map.of(messageDefinition.name(), messageDefinition)),
                null
        );

        Assertions.assertTrue(composer.compose(context, MdfTargetType.EQP).isEmpty());
    }

    @Test
    void shouldFailWhenTemplateNameIsMissing() {
        final MdfMessageDefinition messageDefinition = new MdfMessageDefinition(
                "TOOL_CONDITION_REQUEST",
                MdfTargetType.EQP,
                MdfOutputType.RAW_MESSAGE,
                "EQPID={EQPID}",
                List.of()
        );

        // action_data_index에 mdfTemplateName 없음
        final BusinessWorkflowActionContext context = createContext(
                new MdfRuntimeDefinition(Map.of(messageDefinition.name(), messageDefinition)),
                """
                        {
                          "fields": {
                            "EQPID": "eqpId"
                          }
                        }
                        """
        );

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> composer.compose(context, MdfTargetType.EQP)
        );
    }

    @Test
    void shouldFailWhenTargetDoesNotMatchRequested() {
        final MdfMessageDefinition messageDefinition = new MdfMessageDefinition(
                "TOOL_CONDITION_REPLY",
                MdfTargetType.MES,
                MdfOutputType.KAFKA,
                null,
                List.of()
        );

        final BusinessWorkflowActionContext context = createContext(
                new MdfRuntimeDefinition(Map.of(messageDefinition.name(), messageDefinition)),
                """
                        {
                          "mdfTemplateName": "TOOL_CONDITION_REPLY",
                          "fields": {}
                        }
                        """
        );

        // MES 메시지를 EQP로 요청하면 예외
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> composer.compose(context, MdfTargetType.EQP)
        );
    }

    @Test
    void shouldFailWhenRequiredFieldIsMissingInActionDataIndex() {
        final MdfMessageDefinition messageDefinition = new MdfMessageDefinition(
                "TOOL_CONDITION_REQUEST",
                MdfTargetType.EQP,
                MdfOutputType.RAW_MESSAGE,
                "CMD=REQ EQPID={EQPID}",
                List.of(new MdfFieldDefinition("EQPID", "EQPID", true))
        );

        // action_data_index.fields에 EQPID(var 이름) 없음
        final BusinessWorkflowActionContext context = createContext(
                new MdfRuntimeDefinition(Map.of(messageDefinition.name(), messageDefinition)),
                """
                        {
                          "mdfTemplateName": "TOOL_CONDITION_REQUEST",
                          "fields": {}
                        }
                        """
        );

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> composer.compose(context, MdfTargetType.EQP)
        );
    }

    @Test
    void shouldUseVarAsLookupKeyWhenVarDiffersFromName() {
        // field name="EQPID", var="equipmentId" 이면
        // action_data_index.fields["equipmentId"] 값을 {EQPID} 자리에 채웁니다.
        final MdfMessageDefinition messageDefinition = new MdfMessageDefinition(
                "TOOL_CONDITION_REQUEST",
                MdfTargetType.EQP,
                MdfOutputType.RAW_MESSAGE,
                "CMD=REQ EQPID={EQPID}",
                List.of(new MdfFieldDefinition("EQPID", "equipmentId", true))
        );

        final BusinessWorkflowActionContext context = createContext(
                new MdfRuntimeDefinition(Map.of(messageDefinition.name(), messageDefinition)),
                """
                        {
                          "mdfTemplateName": "TOOL_CONDITION_REQUEST",
                          "fields": {
                            "equipmentId": { "from": "data", "path": "eqpId" }
                          }
                        }
                        """
        );

        final MdfComposeResult result = composer.compose(context, MdfTargetType.EQP).orElseThrow();
        // equipmentId로 lookup → EQP-01 값이 {EQPID} 자리에 들어갑니다.
        Assertions.assertEquals("CMD=REQ EQPID=EQP-01", result.rawMessage());
    }

    /**
     * 테스트용 액션 컨텍스트를 생성합니다.
     */
    private static BusinessWorkflowActionContext createContext(
            final MdfRuntimeDefinition mdfRuntimeDefinition,
            final String actionDataIndex
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
                """
                        {
                          "metadata": {
                            "eventType": "EQP_CONDITION_CHECK"
                          },
                          "data": {
                            "eqpId": "EQP-01",
                            "carId": "CAR-01",
                            "status": "READY"
                          }
                        }
                        """
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
                "PUBLISH_EQP_COMMAND",
                actionDataIndex,
                0
        );

        final BusinessWorkflowFilterContext filterContext = new BusinessWorkflowFilterContext(
                record,
                Map.of(
                        "metadata", Map.of("eventType", "EQP_CONDITION_CHECK"),
                        "data", Map.of("eqpId", "EQP-01", "carId", "CAR-01", "status", "READY")
                ),
                Map.of()
        );

        return new BusinessWorkflowActionContext(
                record,
                runtime,
                workflowEntry,
                filterContext,
                BusinessWorkflowActionMessageType.SECS
        );
    }
}
