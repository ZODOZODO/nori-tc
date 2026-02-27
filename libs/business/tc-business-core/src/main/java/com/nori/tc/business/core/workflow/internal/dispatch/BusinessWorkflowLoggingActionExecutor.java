package com.nori.tc.business.core.workflow.internal.dispatch;

import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionExecutor;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowMatchResult;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 워크플로우 액션 실행을 로그로만 기록하는 대체 실행기입니다.
 *
 * <p>실제 액션 메서드를 호출하지 않고, 매칭 결과와 후보 정보를 로그로 남겨
 * 운영 점검 또는 기능 검증 단계에서 사용할 수 있습니다.</p>
 */
public class BusinessWorkflowLoggingActionExecutor implements BusinessWorkflowActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowLoggingActionExecutor.class);

    @Override
    public void execute(
            final BusinessInboundRecord record,
            final TcModelRuntime modelRuntime,
            final BusinessWorkflowMatchResult matchResult
    ) {
        Objects.requireNonNull(record, "record is null");
        Objects.requireNonNull(modelRuntime, "modelRuntime is null");
        Objects.requireNonNull(matchResult, "matchResult is null");

        if (!matchResult.hasMatchedWorkflow()) {
            return;
        }

        // INFO 레벨에는 전체 실행 요약을, DEBUG 레벨에는 워크플로우 개별 항목을 출력합니다.
        if (log.isInfoEnabled()) {
            log.info("Workflow action stage completed in logging mode. eqpId={}, messageName={}, modelVersionKey={}, matchedCount={}",
                    record.eqpId(),
                    record.messageName(),
                    modelRuntime.modelVersionKey(),
                    matchResult.matchedWorkflows().size());
        }

        if (log.isDebugEnabled()) {
            for (WorkflowRuntimeEntry entry : matchResult.matchedWorkflows()) {
                log.debug("Workflow action candidate. eqpId={}, workflowKey={}, workflowName={}, actionName={}, order={}",
                        record.eqpId(),
                        entry.workflowKey(),
                        entry.workflowName(),
                        entry.actionName(),
                        entry.order());
            }
        }
    }
}



