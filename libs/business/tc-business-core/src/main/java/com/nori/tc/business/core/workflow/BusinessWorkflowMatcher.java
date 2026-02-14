package com.nori.tc.business.core.workflow;

import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;

import java.util.List;
import java.util.Map;

/**
 * non-UI 메시지의 workflow 매칭을 수행하는 포트입니다.
 */
@FunctionalInterface
public interface BusinessWorkflowMatcher {

    /**
     * 입력 레코드와 모델 런타임을 기반으로 workflow를 매칭합니다.
     *
     * @param record inbound 레코드
     * @param modelRuntime 모델 런타임
     * @return 매칭 결과
     */
    BusinessWorkflowMatchResult match(BusinessInboundRecord record, TcModelRuntime modelRuntime);

    /**
     * 테스트/골격 단계에서 사용할 no-op 매처를 반환합니다.
     *
     * @return 항상 매칭 없음 결과를 반환하는 매처
     */
    static BusinessWorkflowMatcher noop() {
        return (record, modelRuntime) -> new BusinessWorkflowMatchResult(
                List.of(),
                new BusinessWorkflowFilterContext(record, Map.of(), Map.of())
        );
    }
}


