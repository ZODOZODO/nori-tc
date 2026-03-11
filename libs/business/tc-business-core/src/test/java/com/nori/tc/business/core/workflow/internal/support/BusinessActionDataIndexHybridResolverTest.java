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
    void shouldResolveHybridFieldSpecsWithTransformChain() {
        final String actionDataIndex = """
                {
                  "mdf": "TOOL_CONDITION_REQUEST_EQP",
                  "fields": {
                    "EQPID": "eqpId",
                    "STATUS": {"var": "data.status", "source": "MSG", "xform": ["trim", "upper"]},
                    "ERRORCODE": {"fixed": "E000"},
                    "OPTIONAL": {"var": "data.missing", "required": false}
                  }
                }
                """;

        final ParsedActionDataIndex parsed = resolver.parse(actionDataIndex);
        final BusinessWorkflowActionContext context = createContext();

        Assertions.assertEquals("TOOL_CONDITION_REQUEST_EQP", parsed.messageName());

        Assertions.assertEquals("EQP-01",
                resolver.resolveFieldValue("EQPID", parsed.fieldSpecs().get("EQPID"), context));
        Assertions.assertEquals("READY",
                resolver.resolveFieldValue("STATUS", parsed.fieldSpecs().get("STATUS"), context));
        Assertions.assertEquals("E000",
                resolver.resolveFieldValue("ERRORCODE", parsed.fieldSpecs().get("ERRORCODE"), context));
        Assertions.assertEquals("",
                resolver.resolveFieldValue("OPTIONAL", parsed.fieldSpecs().get("OPTIONAL"), context));
    }

    @Test
    void shouldFailWhenRequiredValueIsMissing() {
        final ValueSpec requiredMissing = new ValueSpec("data.unknown", MdfRuntimeDefinition.MdfSourceType.MSG, List.of(), null, true);
        final BusinessWorkflowActionContext context = createContext();

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveFieldValue("REQUIRED_FIELD", requiredMissing, context)
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
                "{\"data\":{\"status\":\"  ready  \"}}"
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
                Map.of("data", Map.of("status", "  ready  ")),
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
