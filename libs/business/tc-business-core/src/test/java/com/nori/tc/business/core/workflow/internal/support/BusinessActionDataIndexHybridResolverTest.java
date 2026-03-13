package com.nori.tc.business.core.workflow.internal.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionMessageType;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterContext;
import com.nori.tc.business.core.workflow.internal.support.BusinessActionDataIndexHybridResolver.ParsedActionDataIndex;
import com.nori.tc.business.core.workflow.internal.support.BusinessActionDataIndexHybridResolver.ValueSpec;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition;
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
 * {@link BusinessActionDataIndexHybridResolver} 단위 테스트입니다.
 */
class BusinessActionDataIndexHybridResolverTest {

    private final BusinessActionDataIndexHybridResolver resolver =
            new BusinessActionDataIndexHybridResolver(new ObjectMapper());

    @Test
    void shouldParseMdfTemplateNameAndMixedFieldSpecs() {
        final String actionDataIndex = """
                {
                  "mdfTemplateName": "TOOL_CONDITION_REQUEST_EQP",
                  "fields": {
                    "EQPID": "eqpId",
                    "EVENT_TYPE": {"from": "metadata", "path": "eventType"},
                    "STATUS": {"from": "data", "path": "status", "transforms": ["trim", "upper"]}
                  }
                }
                """;

        final ParsedActionDataIndex parsed = resolver.parse(actionDataIndex);
        final ValueSpec eqpIdSpec = parsed.fields().get("EQPID");
        final ValueSpec eventTypeSpec = parsed.fields().get("EVENT_TYPE");
        final ValueSpec statusSpec = parsed.fields().get("STATUS");

        Assertions.assertEquals("TOOL_CONDITION_REQUEST_EQP", parsed.mdfTemplateName());
        Assertions.assertEquals(BusinessActionDataIndexHybridResolver.LookupSourceType.DATA, eqpIdSpec.lookupSourceType());
        Assertions.assertEquals("eqpId", eqpIdSpec.path());
        Assertions.assertEquals(BusinessActionDataIndexHybridResolver.LookupSourceType.METADATA, eventTypeSpec.lookupSourceType());
        Assertions.assertEquals("eventType", eventTypeSpec.path());
        Assertions.assertEquals("trim", statusSpec.transforms().get(0).name());
        Assertions.assertEquals("upper", statusSpec.transforms().get(1).name());
    }

    @Test
    void shouldResolveCanonicalFieldSpecsWithTransformChain() {
        final String actionDataIndex = """
                {
                  "mdfTemplateName": "TOOL_CONDITION_REQUEST_EQP",
                  "fields": {
                    "EQPID": "eqpId",
                    "STATUS": {"from": "data", "path": "status", "transforms": ["trim", "upper"]},
                    "EVENT_TYPE": {"from": "metadata", "path": "eventType"},
                    "OPTIONAL": {"from": "data", "path": "missing"}
                  }
                }
                """;

        final ParsedActionDataIndex parsed = resolver.parse(actionDataIndex);
        final BusinessWorkflowActionContext context = createContext();

        Assertions.assertEquals("TOOL_CONDITION_REQUEST_EQP", parsed.mdfTemplateName());

        Assertions.assertEquals("EQP-01",
                resolver.resolveFieldValue("EQPID", parsed.fields().get("EQPID"), context));
        Assertions.assertEquals("READY",
                resolver.resolveFieldValue("STATUS", parsed.fields().get("STATUS"), context));
        Assertions.assertEquals("EQP_CONDITION_CHECK",
                resolver.resolveFieldValue("EVENT_TYPE", parsed.fields().get("EVENT_TYPE"), context));
        Assertions.assertEquals("",
                resolver.resolveFieldValue("OPTIONAL", parsed.fields().get("OPTIONAL"), context));
    }

    @Test
    void shouldReturnEmptyWhenFieldValueIsMissing() {
        final ValueSpec missingField = ValueSpec.payloadPath(
                "unknown",
                BusinessActionDataIndexHybridResolver.LookupSourceType.DATA,
                List.of()
        );
        final BusinessWorkflowActionContext context = createContext();

        Assertions.assertEquals(
                "",
                resolver.resolveFieldValue("MISSING_FIELD", missingField, context)
        );
    }

    @Test
    void shouldRejectLegacyActionDataIndexKeys() {
        final String legacyActionDataIndex = """
                {
                  "messageName": "TOOL_CONDITION_REQUEST_EQP",
                  "fields": {
                    "STATUS": {"var": "data.status", "source": "MSG", "xform": ["upper"]}
                  }
                }
                """;

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> resolver.parse(legacyActionDataIndex)
        );
    }

    @Test
    void shouldRejectAbsolutePathInFieldSpec() {
        final String actionDataIndex = """
                {
                  "mdfTemplateName": "TOOL_CONDITION_REQUEST_EQP",
                  "fields": {
                    "STATUS": {"from": "data", "path": "data.status"}
                  }
                }
                """;

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> resolver.parse(actionDataIndex)
        );
    }

    /**
     * 테스트용 액션 컨텍스트를 생성합니다.
     */
    private static BusinessWorkflowActionContext createContext() {
        final BusinessInboundRecord record = new BusinessInboundRecord(
                "tc.eqp.events",
                0,
                10L,
                "EQP-01",
                "TRACE-01",
                BusinessMessageType.EQP,
                "S6F11",
                "payload://eqp/10",
                """
                        {
                          "metadata": {
                            "eventType": "EQP_CONDITION_CHECK"
                          },
                          "data": {
                            "eqpId": "EQP-01",
                            "status": "  ready  "
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
                MdfRuntimeDefinition.empty()
        );

        final WorkflowRuntimeEntry workflowEntry = new WorkflowRuntimeEntry(
                1L,
                "WF-1",
                "S6F11",
                null,
                null,
                null,
                "PUBLISH_EQP_COMMAND",
                null,
                0
        );

        final BusinessWorkflowFilterContext filterContext = new BusinessWorkflowFilterContext(
                record,
                Map.of(
                        "metadata", Map.of("eventType", "EQP_CONDITION_CHECK"),
                        "data", Map.of("eqpId", "EQP-01", "status", "  ready  ")
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
