package com.nori.tc.business.core.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 코어(앱 기본) 액션 레지스트리 컴포넌트입니다.
 *
 * <p>Spring Bean으로 등록된 Executor들을 부팅 시 1회 스캔해
 * 불변 레지스트리를 생성합니다.</p>
 */
@Component
public class BusinessWorkflowCoreActionRegistry {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowCoreActionRegistry.class);

    private final BusinessWorkflowActionRegistry registry;

    /**
     * 코어 액션 Executor 목록을 받아 레지스트리를 구성합니다.
     *
     * @param secsExecutors SECS executor beans
     * @param socketExecutors SOCKET executor beans
     * @param mesExecutors MES executor beans
     */
    public BusinessWorkflowCoreActionRegistry(
            final List<SecsActionExecutor> secsExecutors,
            final List<SocketActionExecutor> socketExecutors,
            final List<MesActionExecutor> mesExecutors
    ) {
        Objects.requireNonNull(secsExecutors, "secsExecutors is null");
        Objects.requireNonNull(socketExecutors, "socketExecutors is null");
        Objects.requireNonNull(mesExecutors, "mesExecutors is null");

        final BusinessWorkflowActionRegistryBuilder builder = new BusinessWorkflowActionRegistryBuilder();
        for (SecsActionExecutor executor : secsExecutors) {
            builder.registerExecutor(executor, BusinessWorkflowActionMessageType.SECS);
        }
        for (SocketActionExecutor executor : socketExecutors) {
            builder.registerExecutor(executor, BusinessWorkflowActionMessageType.SOCKET);
        }
        for (MesActionExecutor executor : mesExecutors) {
            builder.registerExecutor(executor, BusinessWorkflowActionMessageType.MES);
        }

        this.registry = builder.build();

        log.info("Core workflow action registry initialized. actionCount={}, secsExecutors={}, socketExecutors={}, mesExecutors={}",
                registry.size(),
                secsExecutors.size(),
                socketExecutors.size(),
                mesExecutors.size());
        if (log.isDebugEnabled()) {
            log.debug("Core workflow action keys={}", registry.keys());
        }
    }

    /**
     * 생성된 코어 액션 레지스트리를 반환합니다.
     *
     * @return immutable core registry
     */
    public BusinessWorkflowActionRegistry registry() {
        return registry;
    }
}


