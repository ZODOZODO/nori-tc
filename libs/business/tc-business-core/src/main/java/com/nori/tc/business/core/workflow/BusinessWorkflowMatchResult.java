package com.nori.tc.business.core.workflow;

import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;

import java.util.List;
import java.util.Objects;

/**
 * 워크플로우 매칭 결과 모델입니다.
 *
 * <p>매칭된 workflow 목록과 필터 평가 컨텍스트를 함께 보관하여
 * 후속 액션 실행/로깅 단계에서 재사용할 수 있도록 구성합니다.</p>
 */
public record BusinessWorkflowMatchResult(
        List<WorkflowRuntimeEntry> matchedWorkflows,
        BusinessWorkflowFilterContext filterContext
) {

    /**
     * 생성 시 입력값을 방어적으로 검증/복사합니다.
     */
    public BusinessWorkflowMatchResult {
        matchedWorkflows = List.copyOf(Objects.requireNonNull(matchedWorkflows, "matchedWorkflows is null"));
        Objects.requireNonNull(filterContext, "filterContext is null");
    }

    /**
     * 매칭된 workflow가 존재하는지 반환합니다.
     */
    public boolean hasMatchedWorkflow() {
        return !matchedWorkflows.isEmpty();
    }
}


