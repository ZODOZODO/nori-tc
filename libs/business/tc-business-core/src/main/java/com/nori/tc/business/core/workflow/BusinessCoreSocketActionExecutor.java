package com.nori.tc.business.core.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 코어 기본 SOCKET 액션 Executor입니다.
 */
@Component
public class BusinessCoreSocketActionExecutor extends SocketActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(BusinessCoreSocketActionExecutor.class);

    /**
     * SOCKET 공통 로그 액션입니다.
     *
     * @param context action context
     */
    @TcAction("CORE_LOG")
    public void coreLog(final BusinessWorkflowActionContext context) {
        log.info("CORE SOCKET action executed. eqpId={}, messageName={}, workflowKey={}, actionName={}",
                context.record().eqpId(),
                context.record().messageName(),
                context.workflowEntry().workflowKey(),
                context.workflowEntry().actionName());
        if (log.isDebugEnabled()) {
            log.debug("CORE SOCKET action detail. contextVariables={}, messageVariables={}",
                    context.contextVariables(),
                    context.messageVariables());
        }
    }
}


