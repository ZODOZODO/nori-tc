package com.nori.tc.business.core.workflow.api.match;

import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;

import java.util.List;
import java.util.Map;

/**
 * non-UI 메시지를 워크플로우와 매칭하는 포트입니다.
 */
@FunctionalInterface
public interface BusinessWorkflowMatcher {

    /**
     * 인바운드 레코드와 모델 런타임을 기준으로 워크플로우를 매칭합니다.
     *
     * @param record 인바운드 레코드
     * @param modelRuntime 모델 런타임
     * @return 매칭 결과
     */
    BusinessWorkflowMatchResult match(BusinessInboundRecord record, TcModelRuntime modelRuntime);

    /**
     * 항상 미매칭 결과를 반환하는 no-op 매처를 제공합니다.
     *
     * @return no-op 매처
     */
    static BusinessWorkflowMatcher noop() {
        return (record, modelRuntime) -> new BusinessWorkflowMatchResult(
                List.of(),
                new BusinessWorkflowFilterContext(record, Map.of(), Map.of())
        );
    }
}