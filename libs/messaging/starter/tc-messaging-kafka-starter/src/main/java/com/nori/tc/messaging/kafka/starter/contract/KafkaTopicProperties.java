package com.nori.tc.messaging.kafka.starter.contract;

/**
 * Kafka topic properties contract.
 *
 * Each app must provide a @ConfigurationProperties implementation
 * that supplies the topic values from external properties.
 */
public interface KafkaTopicProperties {

    String getEqpEvents();

    String getUiEvents();

    String getEqpCommands();

    String getUiCommands();
}
