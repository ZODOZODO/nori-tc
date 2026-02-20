package com.nori.tc.business.core.workflow.api.action;

import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterContext;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;

import java.util.Map;
import java.util.Objects;

/**
 * 액션 메서드가 참조하는 실행 컨텍스트입니다.
 *
 * <p>액션 구현체는 이 컨텍스트를 통해 record/workflow/filter 변수에 접근합니다.</p>
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
     * 필터 평가에 사용된 MSG 변수 맵을 반환합니다.
     *
     * @return 불변 message 변수 맵
     */
    public Map<String, Object> messageVariables() {
        return filterContext.messageVariables();
    }

    /**
     * 필터 평가에 사용된 CTX 변수 맵을 반환합니다.
     *
     * @return 불변 context 변수 맵
     */
    public Map<String, Object> contextVariables() {
        return filterContext.contextVariables();
    }
}