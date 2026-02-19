package com.nori.tc.business.adapters.kafka.ui;

import com.nori.tc.common.kafka.task.pipeline.KafkaTaskMessageAccessor;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.springframework.stereotype.Component;

/**
 * {@link KafkaUiTaskMessage} 전용 필드 접근자입니다.
 *
 * <p>공통 UI 파이프라인이 요구하는 eventType/traceId/eqpId를
 * 메시지 구조에서 안전하게 추출합니다.</p>
 */
@Component
public class BusinessUiTaskMessageAccessor implements KafkaTaskMessageAccessor<KafkaUiTaskMessage> {

    @Override
    public String eventType(final KafkaUiTaskMessage request) {
        if (request == null || request.metadata() == null) {
            return null;
        }
        return request.metadata().eventType();
    }

    @Override
    public String traceId(final KafkaUiTaskMessage request) {
        if (request == null || request.metadata() == null) {
            return null;
        }
        return request.metadata().traceId();
    }

    @Override
    public String eqpId(final KafkaUiTaskMessage request) {
        if (request == null || request.data() == null) {
            return null;
        }
        return request.data().eqpId();
    }
}


