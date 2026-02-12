package com.nori.tc.messaging.core.port;

/**
 * 기술 중립 메시지 발행 Port
 */
public interface MessagePublisherPort {

    /**
     * 메시징 코어 모듈 메시지 흐름을 처리합니다.
     *
     * <p>기술 중립 포트 계약과 발행 요청 모델 규약을 기준으로 동작합니다.</p>
     * @param request 처리할 요청/명령 객체
     */
    void publish(MessagePublishRequest request) throws Exception;
}
