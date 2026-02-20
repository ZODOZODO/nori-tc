package com.nori.tc.business.core.workflow.internal.core;

import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionMessageType;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistry;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistryBuilder;
import com.nori.tc.business.core.workflow.api.spi.executor.AbstractMesActionExecutor;
import com.nori.tc.business.core.workflow.api.spi.executor.AbstractSecsActionExecutor;
import com.nori.tc.business.core.workflow.api.spi.executor.AbstractSocketActionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * core 영역의 액션 실행기들을 스캔하여 통합 레지스트리를 구성합니다.
 *
 * <p>메시지 타입(SECS/SOCKET/MES)별 실행기 목록을 받아
 * `BusinessWorkflowActionRegistryBuilder`에 등록한 뒤 불변 레지스트리로 보관합니다.</p>
 */
@Component
public class BusinessWorkflowCoreActionRegistry {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowCoreActionRegistry.class);

    private final BusinessWorkflowActionRegistry registry;

    /**
     * 타입별 core 실행기 목록으로 레지스트리를 초기화합니다.
     *
     * @param secsExecutors SECS 타입 실행기 목록
     * @param socketExecutors SOCKET 타입 실행기 목록
     * @param mesExecutors MES 타입 실행기 목록
     */
    public BusinessWorkflowCoreActionRegistry(
            final List<AbstractSecsActionExecutor> secsExecutors,
            final List<AbstractSocketActionExecutor> socketExecutors,
            final List<AbstractMesActionExecutor> mesExecutors
    ) {
        Objects.requireNonNull(secsExecutors, "secsExecutors is null");
        Objects.requireNonNull(socketExecutors, "socketExecutors is null");
        Objects.requireNonNull(mesExecutors, "mesExecutors is null");

        final BusinessWorkflowActionRegistryBuilder builder = new BusinessWorkflowActionRegistryBuilder();
        for (AbstractSecsActionExecutor executor : secsExecutors) {
            builder.registerExecutor(executor, BusinessWorkflowActionMessageType.SECS);
        }
        for (AbstractSocketActionExecutor executor : socketExecutors) {
            builder.registerExecutor(executor, BusinessWorkflowActionMessageType.SOCKET);
        }
        for (AbstractMesActionExecutor executor : mesExecutors) {
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
     * 초기화된 core 액션 레지스트리를 반환합니다.
     *
     * @return 불변(immutable) core 액션 레지스트리
     */
    public BusinessWorkflowActionRegistry registry() {
        return registry;
    }
}




