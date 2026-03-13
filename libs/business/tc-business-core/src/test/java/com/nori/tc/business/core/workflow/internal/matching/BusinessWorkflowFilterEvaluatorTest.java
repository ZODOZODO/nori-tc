package com.nori.tc.business.core.workflow.internal.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterContext;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterEvaluationException;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.runtime.BusinessMessageType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link BusinessWorkflowFilterEvaluator}의 canonical workflow_filter 평가 계약을 검증합니다.
 */
class BusinessWorkflowFilterEvaluatorTest {

    private final BusinessWorkflowFilterEvaluator evaluator = new BusinessWorkflowFilterEvaluator(new ObjectMapper());

    @Test
    void shouldEvaluateSingleConditionSuccessAndFailure() {
        final String workflowFilter = """
                {
                  "and": [
                    {
                      "from": "data",
                      "path": "status",
                      "comparison": "equals",
                      "expected": "READY"
                    }
                  ]
                }
                """;

        Assertions.assertTrue(evaluator.evaluate(
                workflowEntry(workflowFilter),
                filterContext(Map.of("status", "READY"), Map.of())
        ));
        Assertions.assertFalse(evaluator.evaluate(
                workflowEntry(workflowFilter),
                filterContext(Map.of("status", "BLOCK"), Map.of())
        ));
    }

    @Test
    void shouldRequireAllChildrenToMatchForAndGroup() {
        final String workflowFilter = """
                {
                  "and": [
                    {
                      "from": "data",
                      "path": "status",
                      "comparison": "equals",
                      "expected": "READY"
                    },
                    {
                      "from": "data",
                      "path": "retryCount",
                      "comparison": "greater_than_or_equal",
                      "expected": 3
                    }
                  ]
                }
                """;

        Assertions.assertTrue(evaluator.evaluate(
                workflowEntry(workflowFilter),
                filterContext(Map.of("status", "READY", "retryCount", 3), Map.of())
        ));
        Assertions.assertFalse(evaluator.evaluate(
                workflowEntry(workflowFilter),
                filterContext(Map.of("status", "READY", "retryCount", 2), Map.of())
        ));
    }

    @Test
    void shouldMatchWhenAnyChildMatchesForOrGroup() {
        final String workflowFilter = """
                {
                  "or": [
                    {
                      "from": "data",
                      "path": "status",
                      "comparison": "equals",
                      "expected": "READY"
                    },
                    {
                      "from": "data",
                      "path": "retryCount",
                      "comparison": "greater_than_or_equal",
                      "expected": 3
                    }
                  ]
                }
                """;

        Assertions.assertTrue(evaluator.evaluate(
                workflowEntry(workflowFilter),
                filterContext(Map.of("status", "BLOCK", "retryCount", 3), Map.of())
        ));
        Assertions.assertFalse(evaluator.evaluate(
                workflowEntry(workflowFilter),
                filterContext(Map.of("status", "BLOCK", "retryCount", 1), Map.of())
        ));
    }

    @Test
    void shouldEvaluateNestedOrWithinAndGroup() {
        final String workflowFilter = """
                {
                  "and": [
                    {
                      "from": "data",
                      "path": "status",
                      "comparison": "equals",
                      "expected": "READY"
                    },
                    {
                      "or": [
                        {
                          "from": "metadata",
                          "path": "eventType",
                          "comparison": "equals",
                          "expected": "SOCKET_IN"
                        },
                        {
                          "from": "data",
                          "path": "retryCount",
                          "comparison": "greater_than_or_equal",
                          "expected": 5
                        }
                      ]
                    }
                  ]
                }
                """;

        Assertions.assertTrue(evaluator.evaluate(
                workflowEntry(workflowFilter),
                filterContext(
                        Map.of("status", "READY", "retryCount", 1),
                        Map.of("eventType", "SOCKET_IN")
                )
        ));
        Assertions.assertFalse(evaluator.evaluate(
                workflowEntry(workflowFilter),
                filterContext(
                        Map.of("status", "READY", "retryCount", 1),
                        Map.of("eventType", "OTHER_EVENT")
                )
        ));
    }

    @Test
    void shouldEvaluateCompositeExpressionFromDesignExample() {
        final String workflowFilter = """
                {
                  "and": [
                    {
                      "or": [
                        {
                          "from": "data",
                          "path": "A",
                          "comparison": "equals",
                          "expected": "TEST"
                        },
                        {
                          "from": "data",
                          "path": "B",
                          "comparison": "greater_than_or_equal",
                          "expected": 4
                        }
                      ]
                    },
                    {
                      "or": [
                        {
                          "from": "data",
                          "path": "C",
                          "comparison": "equals",
                          "expected": "NO"
                        },
                        {
                          "from": "data",
                          "path": "D",
                          "comparison": "less_than_or_equal",
                          "expected": 10
                        }
                      ]
                    }
                  ]
                }
                """;

        Assertions.assertTrue(evaluator.evaluate(
                workflowEntry(workflowFilter),
                filterContext(Map.of("A", "OTHER", "B", 4, "C", "YES", "D", 10), Map.of())
        ));
        Assertions.assertFalse(evaluator.evaluate(
                workflowEntry(workflowFilter),
                filterContext(Map.of("A", "OTHER", "B", 1, "C", "YES", "D", 11), Map.of())
        ));
    }

    @Test
    void shouldReadValuesFromDataAndMetadataBlocksOnly() {
        final String workflowFilter = """
                {
                  "and": [
                    {
                      "from": "data",
                      "path": "status",
                      "comparison": "equals",
                      "expected": "READY"
                    },
                    {
                      "from": "metadata",
                      "path": "eventType",
                      "comparison": "equals",
                      "expected": "EQP_CONDITION_CHECK"
                    }
                  ]
                }
                """;

        Assertions.assertTrue(evaluator.evaluate(
                workflowEntry(workflowFilter),
                filterContext(
                        Map.of("status", "READY"),
                        Map.of("eventType", "EQP_CONDITION_CHECK")
                )
        ));
    }

    @Test
    void shouldRejectAbsolutePathInWorkflowFilter() {
        final String workflowFilter = """
                {
                  "and": [
                    {
                      "from": "data",
                      "path": "data.status",
                      "comparison": "equals",
                      "expected": "READY"
                    }
                  ]
                }
                """;

        Assertions.assertThrows(
                BusinessWorkflowFilterEvaluationException.class,
                () -> evaluator.evaluate(
                        workflowEntry(workflowFilter),
                        filterContext(Map.of("status", "READY"), Map.of())
                )
        );
    }

    @Test
    void shouldPreservePreviousValueWhenTransformFails() {
        final String workflowFilter = """
                {
                  "and": [
                    {
                      "from": "data",
                      "path": "status",
                      "comparison": "equals",
                      "expected": "READY",
                      "transforms": ["trim"]
                    }
                  ]
                }
                """;

        Assertions.assertTrue(evaluator.evaluate(
                workflowEntry(workflowFilter),
                filterContext(Map.of("status", new ExplodingToStringValue("READY")), Map.of())
        ));
    }

    /**
     * 테스트용 workflow 엔트리를 생성합니다.
     */
    private static WorkflowRuntimeEntry workflowEntry(final String workflowFilter) {
        return new WorkflowRuntimeEntry(
                1L,
                "WF-TEST",
                "SOCKET_IN",
                null,
                null,
                workflowFilter,
                "PUBLISH_EQP_COMMAND",
                null,
                0
        );
    }

    /**
     * 테스트용 filter context를 생성합니다.
     */
    private static BusinessWorkflowFilterContext filterContext(
            final Map<String, Object> dataVariables,
            final Map<String, Object> metadataVariables
    ) {
        final Map<String, Object> messageVariables = new LinkedHashMap<>();
        messageVariables.put("data", Map.copyOf(dataVariables));
        messageVariables.put("metadata", Map.copyOf(metadataVariables));

        return new BusinessWorkflowFilterContext(
                new BusinessInboundRecord(
                        "tc.eqp.events",
                        0,
                        1L,
                        "EQP-01",
                        "TRACE-01",
                        BusinessMessageType.EQP,
                        "SOCKET_IN",
                        "payload://eqp/1",
                        "{\"metadata\":{},\"data\":{}}"
                ),
                Map.copyOf(messageVariables),
                Map.of("eqpId", "EQP-01")
        );
    }

    /**
     * transform 실패 fallback을 검증하기 위한 테스트 전용 값 객체입니다.
     */
    private static final class ExplodingToStringValue {

        private final String stableValue;

        private ExplodingToStringValue(final String stableValue) {
            this.stableValue = stableValue;
        }

        @Override
        public boolean equals(final Object other) {
            return Objects.equals(stableValue, other);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stableValue);
        }

        @Override
        public String toString() {
            throw new IllegalStateException("toString should not be called after transform fallback");
        }
    }
}
