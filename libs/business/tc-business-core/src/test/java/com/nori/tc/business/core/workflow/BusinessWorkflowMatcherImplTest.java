package com.nori.tc.business.core.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterEvaluationException;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowMatchResult;
import com.nori.tc.business.core.workflow.internal.matching.BusinessWorkflowFilterEvaluator;
import com.nori.tc.business.core.workflow.internal.matching.BusinessWorkflowMatcherImpl;
import com.nori.tc.business.core.workflow.internal.matching.BusinessWorkflowPayloadExtractor;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.runtime.BusinessMessageType;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.domain.model.TcModelWorkflow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@link BusinessWorkflowMatcherImpl} 단위 테스트입니다.
 */
class BusinessWorkflowMatcherImplTest {

    @Test
    void shouldMatchHsmsWorkflowByMessageAndSecsKeysAndFilter() {
        final BusinessWorkflowMatcherImpl matcher = createMatcher();
        final TcModelRuntime runtime = createRuntime(
                ProtocolType.SECS,
                List.of(
                        workflow(1L, 100L, "WF-READY", "S6F11", "E1", "T1", filterEq("data.status", "READY")),
                        workflow(2L, 100L, "WF-BLOCK", "S6F11", "E1", "T1", filterEq("data.status", "BLOCK")),
                        workflow(3L, 100L, "WF-OTHER", "S6F11", "E9", "T9", null)
                )
        );

        final BusinessInboundRecord record = new BusinessInboundRecord(
                "tc.eqp.events",
                0,
                10L,
                "EQP-100",
                "TRACE-TEST-10",
                BusinessMessageType.EQP,
                "S6F11",
                "payload://eqp/10",
                "{\"eventId\":\"E1\",\"transactionId\":\"T1\",\"data\":{\"status\":\"READY\"}}"
        );

        final BusinessWorkflowMatchResult result = matcher.match(record, runtime);

        Assertions.assertTrue(result.hasMatchedWorkflow());
        Assertions.assertEquals(1, result.matchedWorkflows().size());
        Assertions.assertEquals("WF-READY", result.matchedWorkflows().getFirst().workflowName());
    }

    @Test
    void shouldReturnEmptyWhenMessageNameIsNotRegistered() {
        final BusinessWorkflowMatcherImpl matcher = createMatcher();
        final TcModelRuntime runtime = createRuntime(
                ProtocolType.SOCKET,
                List.of(workflow(1L, 100L, "WF-A", "SOCKET_IN", null, null, null))
        );

        final BusinessInboundRecord record = new BusinessInboundRecord(
                "tc.eqp.events",
                0,
                11L,
                "EQP-100",
                "TRACE-TEST-11",
                BusinessMessageType.EQP,
                "UNKNOWN_MESSAGE",
                "payload://eqp/11",
                "{\"raw\":\"sample\"}"
        );

        final BusinessWorkflowMatchResult result = matcher.match(record, runtime);

        Assertions.assertFalse(result.hasMatchedWorkflow());
        Assertions.assertTrue(result.matchedWorkflows().isEmpty());
    }

    @Test
    void shouldThrowFilterEvalExceptionWhenWorkflowFilterJsonIsInvalid() {
        final BusinessWorkflowMatcherImpl matcher = createMatcher();
        final TcModelRuntime runtime = createRuntime(
                ProtocolType.SOCKET,
                List.of(workflow(1L, 100L, "WF-BROKEN", "SOCKET_IN", null, null, "{broken-json"))
        );

        final BusinessInboundRecord record = new BusinessInboundRecord(
                "tc.eqp.events",
                0,
                12L,
                "EQP-100",
                "TRACE-TEST-12",
                BusinessMessageType.EQP,
                "SOCKET_IN",
                "payload://eqp/12",
                "{\"raw\":\"sample\"}"
        );

        Assertions.assertThrows(
                BusinessWorkflowFilterEvaluationException.class,
                () -> matcher.match(record, runtime)
        );
    }

    /**
     * 테스트용 matcher를 생성합니다.
     */
    private static BusinessWorkflowMatcherImpl createMatcher() {
        final ObjectMapper objectMapper = new ObjectMapper();
        final BusinessWorkflowPayloadExtractor payloadExtractor = new BusinessWorkflowPayloadExtractor(objectMapper);
        final BusinessWorkflowFilterEvaluator filterEvaluator = new BusinessWorkflowFilterEvaluator(objectMapper);
        return new BusinessWorkflowMatcherImpl(payloadExtractor, filterEvaluator);
    }

    /**
     * 테스트용 TcModelRuntime을 생성합니다.
     */
    private static TcModelRuntime createRuntime(
            final ProtocolType protocolType,
            final List<TcModelWorkflow> workflows
    ) {
        final OffsetDateTime now = OffsetDateTime.now();
        final TcModel model = new TcModel(
                100L,
                100L,
                "MODEL-100",
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

        final List<WorkflowRuntimeEntry> entries = workflows.stream()
                .map(workflow -> WorkflowRuntimeEntry.from(workflow, (int) workflow.workflowKey()))
                .toList();

        return TcModelRuntime.from(
                model,
                entries,
                List.of(),
                List.of(),
                List.of()
        );
    }

    /**
     * 테스트용 workflow 레코드를 생성합니다.
     */
    private static TcModelWorkflow workflow(
            final long workflowKey,
            final long modelVersionKey,
            final String workflowName,
            final String messageName,
            final String eventId,
            final String transactionId,
            final String workflowFilter
    ) {
        return new TcModelWorkflow(
                workflowKey,
                modelVersionKey,
                workflowName,
                messageName,
                eventId,
                transactionId,
                workflowFilter,
                "ACTION-" + workflowName,
                null,
                OffsetDateTime.now()
        );
    }

    /**
     * 단순 equality 필터 JSON을 생성합니다.
     */
    private static String filterEq(final String variablePath, final String expectedValue) {
        return "{\"rows\":[{\"left\":{\"var\":{\"name\":\""
                + variablePath
                + "\",\"source\":\"MSG\"}},\"op\":\"eq\",\"right\":\""
                + expectedValue
                + "\"}]}";
    }
}

