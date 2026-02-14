package com.nori.tc.business.core.workflow;

import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * workflow action 실행 기본 구현체입니다.
 *
 * <p>현재 step12 범위에서는 실제 도메인 action 호출 대신,
 * 매칭된 workflow/action 정보를 로그로 남기고 정상 종료합니다.
 * 이후 플러그인 실행기가 준비되면 이 구현을 교체하면 됩니다.</p>
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

        /*
         * 실제 action executor 도입 전까지는 매칭 결과를 추적 가능한 로그로 남기고 통과시킵니다.
         * 운영 추적에서 eqpId/messageName/workflowKey/actionName/order가 핵심 식별자입니다.
         */
        if (log.isInfoEnabled()) {
            log.info("Workflow action stage completed in logging mode. eqpId={}, messageName={}, modelKey={}, matchedCount={}",
                    record.eqpId(),
                    record.messageName(),
                    modelRuntime.modelKey(),
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


