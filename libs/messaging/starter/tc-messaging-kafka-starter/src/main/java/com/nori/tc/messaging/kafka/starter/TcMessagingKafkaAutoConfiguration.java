package com.nori.tc.messaging.kafka.starter;

import com.nori.tc.messaging.core.port.MessagePublisherPort;
import com.nori.tc.messaging.kafka.adapter.KafkaMessagePublisher;
import com.nori.tc.messaging.kafka.config.TcKafkaProperties;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Kafka Messaging AutoConfiguration
 */
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties(TcKafkaProperties.class)
public class TcMessagingKafkaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MessagePublisherPort.class)
    public MessagePublisherPort messagePublisherPort(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final TcKafkaProperties properties
    ) {
        return new KafkaMessagePublisher(kafkaTemplate, properties);
    }
}
