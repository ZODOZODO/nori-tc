package com.nori.tc.common.ui.task.pipeline;

/**
 * UI 이벤트 처리기 계약입니다.
 *
 * @param <T> 요청 타입
 */
@FunctionalInterface
public interface UiTaskProcessor<T> {

    /**
     * 단일 UI 요청을 처리하고 결과를 반환합니다.
     *
     * @param request 요청 원문
     * @return 처리 결과
     * @throws Exception 처리 중 예외
     */
    UiTaskResult process(T request) throws Exception;
}
