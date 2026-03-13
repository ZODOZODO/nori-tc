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
import java.util.List;
import java.util.Map;

/**
 * {@link BusinessMdfMessageComposer} 단위 테스트입니다.
 */
class BusinessMdfMessageComposerTest {

    private final BusinessMdfMessageComposer composer =
            new BusinessMdfMessageComposer(new BusinessActionDataIndexHybridResolver(new ObjectMapper()));

    @Test
    void shouldComposeEqpMessageUsingMdfAndActionDataIndexOverride() {
        final MdfMessageDefinition messageDefinition = new MdfMessageDefinition(
                "TOOL_CONDITION_REQUEST_EQP",
                MdfTargetType.EQP,
                MdfOutputType.RAW_MESSAGE,
                "PUBLISH_EQP_COMMAND",
                "CMD=REQ EQPID={EQPID} STATUS={STATUS}",
                List.of(
                        new MdfFieldDefinition("EQPID", "eqpId", MdfSourceType.CTX, List.of(), null, true),
                        new MdfFieldDefinition("STATUS", "data.status", MdfSourceType.MSG, List.of("upper"), null, true)
                )
        );

        final BusinessWorkflowActionContext context = createContext(
                new MdfRuntimeDefinition(Map.of(messageDefinition.name(), messageDefinition)),
                """
                        {
                          "mdfTemplateName": "TOOL_CONDITION_REQUEST_EQP",
                          "fields": {
                            "STATUS": {
                              "from": "data",
                              "path": "overrideStatus",
                              "transforms": ["upper"]
                            }
                          }
                        }
                        """
        );

        final MdfComposeResult result = composer.compose(context, MdfTargetType.EQP)
                .orElseThrow();

        Assertions.assertEquals("CMD=REQ EQPID=EQP-01 STATUS=MANUAL", result.renderedMessage());
        Assertions.assertEquals("EQP-01", result.fieldValues().get("EQPID"));
        Assertions.assertEquals("MANUAL", result.fieldValues().get("STATUS"));
    }

    @Test
    void shouldReturnEmptyWhenActionDataIndexIsEmptyEvenIfMatchingMdfExists() {
        final MdfMessageDefinition messageDefinition = new MdfMessageDefinition(
                "REQ_A_EQP",
                MdfTargetType.EQP,
                MdfOutputType.RAW_MESSAGE,
                "PUBLISH_EQP_COMMAND",
                "A={A}",
                List.of(new MdfFieldDefinition("A", "a", MdfSourceType.MSG, List.of(), null, false))
        );

        final BusinessWorkflowActionContext context = createContext(
                new MdfRuntimeDefinition(Map.of(messageDefinition.name(), messageDefinition)),
                null
        );

        Assertions.assertTrue(composer.compose(context, MdfTargetType.EQP).isEmpty());
    }

    @Test
    void shouldFailWhenTemplateTargetDoesNotMatchRequestedTarget() {
        final MdfMessageDefinition messageDefinition = new MdfMessageDefinition(
                "TOOL_CONDITION_REPLY_MES",
                MdfTargetType.MES,
                MdfOutputType.DATA,
                "PUBLISH_MES_COMMAND",
                "EVENT={EVENT_TYPE}",
                List.of()
        );

        final BusinessWorkflowActionContext context = createContext(
                new MdfRuntimeDefinition(Map.of(messageDefinition.name(), messageDefinition)),
                """
                        {
                          "mdfTemplateName": "TOOL_CONDITION_REPLY_MES",
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
    void shouldResolveMetadataAndFixedMdfFieldFallbackWhenNoActionOverrideExists() {
        final MdfMessageDefinition messageDefinition = new MdfMessageDefinition(
                "TOOL_METADATA_REPLY_EQP",
                MdfTargetType.EQP,
                MdfOutputType.RAW_MESSAGE,
                "PUBLISH_EQP_COMMAND",
                "EVENT={EVENT_TYPE} FIXED={FIXED_VALUE}",
                List.of(
                        new MdfFieldDefinition("EVENT_TYPE", "metadata.eventType", MdfSourceType.MSG, List.of(), null, true),
                        new MdfFieldDefinition("FIXED_VALUE", null, MdfSourceType.AUTO, List.of(), "CONST", true)
                )
        );

        final BusinessWorkflowActionContext context = createContext(
                new MdfRuntimeDefinition(Map.of(messageDefinition.name(), messageDefinition)),
                """
                        {
                          "mdfTemplateName": "TOOL_METADATA_REPLY_EQP",
                          "fields": {}
                        }
                        """
        );

        final MdfComposeResult result = composer.compose(context, MdfTargetType.EQP).orElseThrow();

        Assertions.assertEquals("EVENT=EQP_CONDITION_CHECK FIXED=CONST", result.renderedMessage());
        Assertions.assertEquals("EQP_CONDITION_CHECK", result.fieldValues().get("EVENT_TYPE"));
        Assertions.assertEquals("CONST", result.fieldValues().get("FIXED_VALUE"));
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
                            "status": "ready"
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
                        "data", Map.of("status", "ready", "overrideStatus", "manual"),
                        "a", "1",
                        "b", "2"
                ),
                Map.of("eqpId", "EQP-01")
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
