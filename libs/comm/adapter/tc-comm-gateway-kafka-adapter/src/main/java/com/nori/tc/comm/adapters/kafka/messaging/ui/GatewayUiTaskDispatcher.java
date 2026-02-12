package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.messaging.kafka.starter.contract.KafkaMessageDispatcher;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * UI task 메시지를 이벤트 타입별 핸들러로 분배하는 디스패처입니다.
 *
 * <p>switch 분기 대신 handler registry를 사용해 확장 시 영향 범위를 최소화합니다.</p>
 */
@Component
public class GatewayUiTaskDispatcher implements KafkaMessageDispatcher<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskDispatcher.class);

    private final Map<KafkaUiTaskEventType, GatewayUiTaskHandler> handlersByType;

    /**
     * 등록된 핸들러 목록으로 이벤트 타입 매핑 테이블을 구성합니다.
     */
    public GatewayUiTaskDispatcher(final List<GatewayUiTaskHandler> handlers) {
        Objects.requireNonNull(handlers, "handlers is null");

        final Map<KafkaUiTaskEventType, GatewayUiTaskHandler> mapped = new EnumMap<>(KafkaUiTaskEventType.class);
        for (GatewayUiTaskHandler handler : handlers) {
            final GatewayUiTaskHandler previous = mapped.put(handler.eventType(), handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate UI task handler for eventType=" + handler.eventType());
            }
        }
        this.handlersByType = mapped;
        log.info("UI task handlers initialized. count={}, eventTypes={}", handlersByType.size(), handlersByType.keySet());
    }

    /**
     * 수신한 UI task를 이벤트 타입에 맞는 핸들러로 전달합니다.
     */
    @Override
    public void dispatch(final KafkaUiTaskMessage message) {
        Objects.requireNonNull(message, "message is null");

        final KafkaUiTaskEventType eventType;
        try {
            eventType = KafkaUiTaskEventType.fromText(message.metadata().eventType());
        } catch (Exception ex) {
            log.warn("UI task ignored (unsupported eventType). eventType={}, eqpId={}, traceId={}",
                    message.metadata().eventType(),
                    message.data().eqpId(),
                    message.metadata().traceId());
            return;
        }

        final GatewayUiTaskHandler handler = handlersByType.get(eventType);
        if (handler == null) {
            log.warn("UI task ignored (no handler). eventType={}, eqpId={}, traceId={}",
                    eventType,
                    message.data().eqpId(),
                    message.metadata().traceId());
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("UI task dispatch start. eventType={}, handler={}, eqpId={}, traceId={}",
                    eventType,
                    handler.getClass().getSimpleName(),
                    message.data().eqpId(),
                    message.metadata().traceId());
        }
        handler.handle(message);
    }
}
