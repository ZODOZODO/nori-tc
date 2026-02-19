package com.nori.tc.business.core.ui;

import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskDispatchReport;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskResult;

/**
 * BusinessUiTaskExecutor 인터페이스입니다.
 *
 * <p>해당 모듈에서 공통 계약과 동작 경계를 정의하며,
 * 호출 계층에서 일관된 사용이 가능하도록 설계되었습니다.</p>
 */
@FunctionalInterface
public interface BusinessUiTaskExecutor {

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    KafkaTaskDispatchReport execute(BusinessInboundRecord record) throws Exception;

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    static BusinessUiTaskExecutor noop() {
        return record -> new KafkaTaskDispatchReport(
                KafkaTaskResult.pass(),
                (record == null || record.messageName() == null ? "UI_TASK" : record.messageName()) + "_REP",
                false
        );
    }
}




