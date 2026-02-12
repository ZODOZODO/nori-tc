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

    /**
     * 메시징 스타터 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>Spring Boot 자동 구성과 메시징 계약 인터페이스를 기준으로 처리합니다.</p>
     * @param kafkaTemplate 메시징 스타터 모듈 처리에 사용하는 입력 값
     * @param properties 모듈 설정 값 객체
     * @return 메시징 스타터 모듈 처리 결과
     */
    @Bean
    @ConditionalOnMissingBean(MessagePublisherPort.class)
    public MessagePublisherPort messagePublisherPort(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final TcKafkaProperties properties
    ) {
        return new KafkaMessagePublisher(kafkaTemplate, properties);
    }
}
