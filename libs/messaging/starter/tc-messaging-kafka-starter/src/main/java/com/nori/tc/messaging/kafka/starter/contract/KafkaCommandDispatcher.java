package com.nori.tc.messaging.kafka.starter.contract;

/**
 * Dispatcher contract for inbound Kafka command messages.
 *
 * Implementations live in each app so that app-specific validation
 * and routing rules can be applied without modifying the starter.
 */
public interface KafkaCommandDispatcher {

    void dispatch(KafkaCommandMessage command);
}
