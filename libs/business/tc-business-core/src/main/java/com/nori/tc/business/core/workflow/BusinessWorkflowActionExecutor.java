package com.nori.tc.business.core.workflow;

import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;

/**
 * 매칭된 workflow의 action 실행을 담당하는 포트입니다.
 */
@FunctionalInterface
public interface BusinessWorkflowActionExecutor {

    /**
     * 매칭된 workflow 목록에 대해 action 실행을 수행합니다.
     *
     * @param record inbound 레코드
     * @param modelRuntime 모델 런타임
     * @param matchResult workflow 매칭 결과
     */
    void execute(BusinessInboundRecord record, TcModelRuntime modelRuntime, BusinessWorkflowMatchResult matchResult);

    /**
     * 테스트/골격 단계에서 사용할 no-op 실행기를 반환합니다.
     *
     * @return 아무 동작도 하지 않는 실행기
     */
    static BusinessWorkflowActionExecutor noop() {
        return (record, modelRuntime, matchResult) -> {
            // no-op
        };
    }
}


