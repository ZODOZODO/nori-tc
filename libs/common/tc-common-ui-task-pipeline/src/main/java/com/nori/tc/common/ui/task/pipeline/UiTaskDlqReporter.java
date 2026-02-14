package com.nori.tc.common.ui.task.pipeline;

/**
 * UI 파이프라인 실패를 DLQ sink에 전달하는 계약입니다.
 *
 * @param <T> 요청 타입
 */
@FunctionalInterface
public interface UiTaskDlqReporter<T> {

    /**
     * UI 작업 실패를 DLQ 대상으로 보고합니다.
     *
     * @param request 원본 요청
     * @param stage 실패가 발생한 파이프라인 단계
     * @param reasonCode 실패 사유 코드
     * @param reasonMessage 실패 상세 메시지
     * @param replyEventType 응답 이벤트 타입
     */
    void report(
            T request,
            UiTaskPipelineStage stage,
            String reasonCode,
            String reasonMessage,
            String replyEventType
    );
}
