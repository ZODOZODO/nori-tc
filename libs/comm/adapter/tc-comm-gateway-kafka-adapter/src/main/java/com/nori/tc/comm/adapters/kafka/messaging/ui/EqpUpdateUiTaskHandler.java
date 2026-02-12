package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import org.springframework.stereotype.Component;

/**
 * {@code EQP_UPDATE} 이벤트 처리기입니다.
 *
 * <p>실제 처리 본문은 상위 {@link AbstractCreateUpdateUiTaskHandler}에 위임하고,
 * 실패 응답 이벤트 타입만 이 클래스에서 정의합니다.</p>
 */
@Component
public class EqpUpdateUiTaskHandler extends AbstractCreateUpdateUiTaskHandler {

    /**
     * UPDATE 공통 처리에 필요한 의존성을 주입받습니다.
     */
    public EqpUpdateUiTaskHandler(
            final GatewayUiRuntimeControlService runtimeControlService,
            final KafkaUiReplyPublisher replyPublisher
    ) {
        super(runtimeControlService, replyPublisher);
    }

    /**
     * UPDATE 실패 시 UI로 발행할 응답 이벤트 타입을 반환합니다.
     */
    @Override
    protected String failReplyEventType() {
        return "EQP_UPDATE_REP";
    }

    /**
     * 이 핸들러가 담당하는 UI 이벤트 타입을 반환합니다.
     */
    @Override
    public KafkaUiTaskEventType eventType() {
        return KafkaUiTaskEventType.EQP_UPDATE;
    }
}
