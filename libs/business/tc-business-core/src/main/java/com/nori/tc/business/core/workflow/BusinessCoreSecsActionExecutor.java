package com.nori.tc.business.core.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 코어 기본 SECS 액션 Executor입니다.
 *
 * <p>현재 단계에서는 샘플 액션(로그 기록)만 제공하며,
 * 실제 도메인 액션은 플러그인 또는 후속 단계에서 확장합니다.</p>
 */
@Component
public class BusinessCoreSecsActionExecutor extends SecsActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(BusinessCoreSecsActionExecutor.class);

    /**
     * SECS 공통 로그 액션입니다.
     *
     * @param context action context
     */
    @TcAction("CORE_LOG")
    public void coreLog(final BusinessWorkflowActionContext context) {
        log.info("CORE SECS action executed. eqpId={}, messageName={}, workflowKey={}, actionName={}",
                context.record().eqpId(),
                context.record().messageName(),
                context.workflowEntry().workflowKey(),
                context.workflowEntry().actionName());
        if (log.isDebugEnabled()) {
            log.debug("CORE SECS action detail. contextVariables={}, messageVariables={}",
                    context.contextVariables(),
                    context.messageVariables());
        }
    }
}


