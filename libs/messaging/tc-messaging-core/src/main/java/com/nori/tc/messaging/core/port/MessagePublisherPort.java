package com.nori.tc.messaging.core.port;

/**
 * 기술 중립 메시지 발행 Port
 */
public interface MessagePublisherPort {

    void publish(MessagePublishRequest request) throws Exception;
}
