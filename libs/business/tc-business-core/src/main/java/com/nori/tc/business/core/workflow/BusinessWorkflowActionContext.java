package com.nori.tc.business.core.workflow;

import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;

import java.util.Map;
import java.util.Objects;

/**
 * 액션 메서드가 참조할 실행 컨텍스트입니다.
 *
 * <p>액션 구현자는 이 컨텍스트를 통해 record/workflow/filter 변수에 접근합니다.</p>
 */
public record BusinessWorkflowActionContext(
        BusinessInboundRecord record,
        TcModelRuntime modelRuntime,
        WorkflowRuntimeEntry workflowEntry,
        BusinessWorkflowFilterContext filterContext,
        BusinessWorkflowActionMessageType actionMessageType
) {

    /**
     * 컨텍스트 생성 시 필수 값 검증을 수행합니다.
     */
    public BusinessWorkflowActionContext {
        Objects.requireNonNull(record, "record is null");
        Objects.requireNonNull(modelRuntime, "modelRuntime is null");
        Objects.requireNonNull(workflowEntry, "workflowEntry is null");
        Objects.requireNonNull(filterContext, "filterContext is null");
        Objects.requireNonNull(actionMessageType, "actionMessageType is null");
    }

    /**
     * 필터 평가 시 사용된 MSG 변수 맵을 반환합니다.
     *
     * @return immutable message variable map
     */
    public Map<String, Object> messageVariables() {
        return filterContext.messageVariables();
    }

    /**
     * 필터 평가 시 사용된 CTX 변수 맵을 반환합니다.
     *
     * @return immutable context variable map
     */
    public Map<String, Object> contextVariables() {
        return filterContext.contextVariables();
    }
}



