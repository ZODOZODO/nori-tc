package com.nori.tc.business.core.ui;

import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.common.ui.task.pipeline.UiTaskDispatchReport;
import com.nori.tc.common.ui.task.pipeline.UiTaskResult;

/**
 * 런타임 worker에서 UI task를 실행하는 포트입니다.
 */
@FunctionalInterface
public interface BusinessUiTaskExecutor {

    /**
     * UI inbound record를 처리하고 REP 결과를 반환합니다.
     *
     * @param record UI inbound record
     * @return dispatch report
     * @throws Exception 파이프라인 처리 중 예외
     */
    UiTaskDispatchReport execute(BusinessInboundRecord record) throws Exception;

    /**
     * 테스트/골격 단계에서 사용할 no-op 실행기를 반환합니다.
     *
     * @return no-op 실행기
     */
    static BusinessUiTaskExecutor noop() {
        return record -> new UiTaskDispatchReport(
                UiTaskResult.pass(),
                (record == null || record.messageName() == null ? "UI_TASK" : record.messageName()) + "_REP",
                false
        );
    }
}


