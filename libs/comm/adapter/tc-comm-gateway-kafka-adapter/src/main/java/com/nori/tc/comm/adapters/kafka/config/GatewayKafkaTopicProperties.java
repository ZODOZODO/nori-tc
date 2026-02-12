package com.nori.tc.comm.adapters.kafka.config;

import com.nori.tc.messaging.kafka.starter.contract.KafkaTopicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;

/**
 * 게이트웨이 Kafka 토픽 설정 바인딩.
 *
 * - tc.messaging.kafka.topic.* 프로퍼티를 바인딩한다
 * - kafka starter의 KafkaTopicProperties 계약을 구현한다
 */
@ConfigurationProperties(prefix = "tc.messaging.kafka.topic")
public class GatewayKafkaTopicProperties implements KafkaTopicProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewayKafkaTopicProperties.class);

    private String eqpEvents;
    private String uiEvents;
    private String eqpCommands;
    private String uiCommands;

    
    /**
     * 게이트웨이 Kafka 어댑터 입력/설정 유효성을 검증합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     */
    @PostConstruct
    public void validate() {
        requireText("tc.messaging.kafka.topic.eqp-events", eqpEvents);
        requireText("tc.messaging.kafka.topic.ui-events", uiEvents);
        requireText("tc.messaging.kafka.topic.eqp-commands", eqpCommands);
        requireText("tc.messaging.kafka.topic.ui-commands", uiCommands);
        log.info("GatewayKafkaTopicProperties validated. eqpEvents={}, uiEvents={}, eqpCommands={}, uiCommands={}",
                eqpEvents, uiEvents, eqpCommands, uiCommands);
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    @Override
    public String getEqpEvents() {
        return eqpEvents;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param eqpEvents 처리할 이벤트 정보
     */
    public void setEqpEvents(final String eqpEvents) {
        this.eqpEvents = eqpEvents;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    @Override
    public String getUiEvents() {
        return uiEvents;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param uiEvents 처리할 이벤트 정보
     */
    public void setUiEvents(final String uiEvents) {
        this.uiEvents = uiEvents;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    @Override
    public String getEqpCommands() {
        return eqpCommands;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param eqpCommands 처리할 요청/명령 정보
     */
    public void setEqpCommands(final String eqpCommands) {
        this.eqpCommands = eqpCommands;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    @Override
    public String getUiCommands() {
        return uiCommands;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param uiCommands 처리할 요청/명령 정보
     */
    public void setUiCommands(final String uiCommands) {
        this.uiCommands = uiCommands;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 입력/설정 유효성을 검증합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @param value 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     */
    private static void requireText(final String key, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing Kafka topic property: " + key);
        }
    }
}
