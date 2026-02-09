package com.nori.tc.apps.commgateway.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Kafka 커맨드 구독 리스너
 */
@Component
public class KafkaCommandListener {

    private final GatewayCommandDispatcher dispatcher;

    public KafkaCommandListener(final GatewayCommandDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher is null");
    }

    @KafkaListener(topics = "${tc.messaging.kafka.topic.eqp-commands}")
    public void onEqpCommand(final GatewayCommandMessage command) {
        dispatcher.dispatch(command);
    }

    @KafkaListener(topics = "${tc.messaging.kafka.topic.ui-commands}")
    public void onUiCommand(final GatewayCommandMessage command) {
        dispatcher.dispatch(command);
    }
}
