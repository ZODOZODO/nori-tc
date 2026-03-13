package com.nori.tc.ui.adapters.web.controller.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ModelDetailPreviewSupport}의 workflow/data index preview 규칙을 검증합니다.
 */
class ModelDetailPreviewSupportTest {

    @Test
    @DisplayName("workflow_filter preview는 첫 번째 조건을 읽기 쉬운 한 줄로 변환합니다")
    void summarizeWorkflowFilterBuildsReadableFirstRowPreview() {
        final String workflowFilter = """
                {
                  "rows": [
                    {
                      "left": {
                        "var": { "name": "status", "source": "MSG" },
                        "xform": ["trim", "lower"]
                      },
                      "op": "eq",
                      "right": "ok"
                    }
                  ]
                }
                """;

        final String preview = ModelDetailPreviewSupport.summarizeWorkflowFilter(workflowFilter);

        assertEquals("status[MSG] | trim | lower eq ok", preview);
    }

    @Test
    @DisplayName("action_data_index preview는 첫 번째 필드 기준으로 message/field 요약을 만듭니다")
    void summarizeActionDataIndexBuildsReadableFirstFieldPreview() {
        final String actionDataIndex = """
                {
                  "mdf": "TOOL_CONDITION_REPLY_MES",
                  "fields": {
                    "EQPID": { "var": "eqpId", "source": "CTX", "required": true },
                    "STATUS": { "var": "data.status", "source": "MSG", "xform": ["trim", "upper"] }
                  }
                }
                """;

        final String preview = ModelDetailPreviewSupport.summarizeActionDataIndex(actionDataIndex);

        assertEquals("TOOL_CONDITION_REPLY_MES / EQPID <- eqpId[CTX]", preview);
    }

    @Test
    @DisplayName("JSON 파싱이 불가능하면 첫 번째 텍스트 라인을 그대로 사용합니다")
    void summarizeFallsBackToFirstMeaningfulLine() {
        final String raw = """

                더블 클릭 없이 바로 보일 첫 줄
                두 번째 줄
                """;

        assertEquals("더블 클릭 없이 바로 보일 첫 줄", ModelDetailPreviewSupport.summarizeWorkflowFilter(raw));
        assertEquals("더블 클릭 없이 바로 보일 첫 줄", ModelDetailPreviewSupport.summarizeActionDataIndex(raw));
    }
}
