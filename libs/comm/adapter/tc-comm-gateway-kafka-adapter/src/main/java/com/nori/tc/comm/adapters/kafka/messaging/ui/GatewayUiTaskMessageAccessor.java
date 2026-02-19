package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.common.kafka.task.pipeline.KafkaTaskMessageAccessor;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.springframework.stereotype.Component;

/**
 * Gateway UI 메시지의 공통 파이프라인 접근자 구현입니다.
 *
 * <p>공통 파이프라인이 필요한 최소 필드(eventType/traceId/eqpId)를
 * gateway 전용 메시지 모델({@link KafkaUiTaskMessage})에서 추출합니다.</p>
 */
@Component
public class GatewayUiTaskMessageAccessor implements KafkaTaskMessageAccessor<KafkaUiTaskMessage> {

    /**
     * 요청에서 eventType을 추출합니다.
     *
     * @param request UI 요청 메시지
     * @return eventType
     */
    @Override
    public String eventType(final KafkaUiTaskMessage request) {
        if (request == null || request.metadata() == null) {
            return null;
        }
        return request.metadata().eventType();
    }

    /**
     * 요청에서 traceId를 추출합니다.
     *
     * @param request UI 요청 메시지
     * @return traceId
     */
    @Override
    public String traceId(final KafkaUiTaskMessage request) {
        if (request == null || request.metadata() == null) {
            return null;
        }
        return request.metadata().traceId();
    }

    /**
     * 요청에서 eqpId를 추출합니다.
     *
     * @param request UI 요청 메시지
     * @return eqpId
     */
    @Override
    public String eqpId(final KafkaUiTaskMessage request) {
        if (request == null || request.data() == null) {
            return null;
        }
        return request.data().eqpId();
    }
}

