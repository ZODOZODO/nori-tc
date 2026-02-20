package com.nori.tc.business.core.workflow.api.action;

import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowMatchResult;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;

/**
 * 매칭된 워크플로우의 액션 실행을 담당하는 포트입니다.
 */
@FunctionalInterface
public interface BusinessWorkflowActionExecutor {

    /**
     * 매칭 결과에 포함된 액션을 순서대로 실행합니다.
     *
     * @param record 인바운드 메시지 레코드
     * @param modelRuntime 모델 런타임
     * @param matchResult 워크플로우 매칭 결과
     */
    void execute(BusinessInboundRecord record, TcModelRuntime modelRuntime, BusinessWorkflowMatchResult matchResult);

    /**
     * 아무 동작도 하지 않는 no-op 실행기를 반환합니다.
     *
     * @return no-op 실행기
     */
    static BusinessWorkflowActionExecutor noop() {
        return (record, modelRuntime, matchResult) -> {
            // no-op
        };
    }
}