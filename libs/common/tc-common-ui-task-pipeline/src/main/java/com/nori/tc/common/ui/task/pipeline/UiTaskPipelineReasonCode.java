package com.nori.tc.common.ui.task.pipeline;

/**
 * UI 파이프라인 DLQ 보고 시 사용하는 기본 사유 코드입니다.
 */
public final class UiTaskPipelineReasonCode {

    /**
     * 라우팅 단계 실패
     */
    public static final String ROUTING_FAILED = "ROUTING_FAILED";

    /**
     * 처리 단계 실패
     */
    public static final String PROCESS_FAILED = "PROCESS_FAILED";

    /**
     * 응답 발행 단계 실패
     */
    public static final String PUBLISH_FAILED = "PUBLISH_FAILED";

    private UiTaskPipelineReasonCode() {
    }
}
