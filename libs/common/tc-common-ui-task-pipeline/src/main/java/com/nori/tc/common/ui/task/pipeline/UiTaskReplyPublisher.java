package com.nori.tc.common.ui.task.pipeline;

/**
 * UI 파이프라인 응답 발행 계약입니다.
 *
 * @param <T> 요청 타입
 */
@FunctionalInterface
public interface UiTaskReplyPublisher<T> {

    /**
     * UI 처리 결과 응답을 발행합니다.
     *
     * @param request 원본 요청
     * @param replyEventType 응답 이벤트 타입
     * @param result 처리 결과
     * @throws Exception 발행 실패 예외
     */
    void publishResult(T request, String replyEventType, UiTaskResult result) throws Exception;
}
