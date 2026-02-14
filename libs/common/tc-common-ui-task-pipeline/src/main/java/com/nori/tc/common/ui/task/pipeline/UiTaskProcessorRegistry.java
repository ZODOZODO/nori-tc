package com.nori.tc.common.ui.task.pipeline;

import java.util.Optional;

/**
 * eventType 기반 UI 처리기 조회 레지스트리 계약입니다.
 *
 * @param <T> 요청 타입
 */
@FunctionalInterface
public interface UiTaskProcessorRegistry<T> {

    /**
     * eventType에 해당하는 처리기 사양을 조회합니다.
     *
     * @param eventType 정규화된 이벤트 타입
     * @return 처리기 사양(없으면 empty)
     */
    Optional<UiTaskProcessorSpec<T>> find(String eventType);
}
