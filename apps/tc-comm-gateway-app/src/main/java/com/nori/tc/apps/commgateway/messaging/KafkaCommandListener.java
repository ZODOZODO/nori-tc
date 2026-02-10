package com.nori.tc.apps.commgateway.messaging;

import com.nori.tc.messaging.kafka.starter.contract.KafkaCommandDispatcher;
import com.nori.tc.messaging.kafka.starter.contract.KafkaCommandMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Kafka command listener.
 *
 * - Consumes command topics and delegates to KafkaCommandDispatcher.
 * - Uses the shared KafkaCommandMessage contract for JSON binding.
 */
@Component
public class KafkaCommandListener {

    private final KafkaCommandDispatcher dispatcher;

    public KafkaCommandListener(final KafkaCommandDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher is null");
    }

    @KafkaListener(topics = "${tc.messaging.kafka.topic.eqp-commands}")
    public void onEqpCommand(final KafkaCommandMessage command) {
        dispatcher.dispatch(command);
    }

    @KafkaListener(topics = "${tc.messaging.kafka.topic.ui-commands}")
    public void onUiCommand(final KafkaCommandMessage command) {
        dispatcher.dispatch(command);
    }
}
