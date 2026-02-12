package com.nori.tc.comm.core.port;

import com.nori.tc.comm.gateway.domain.dlq.DlqMessage;

/**
 * DLQ 발행 Port
 *
 * 저장 대상 예)
 * - DB 테이블
 * - Kafka topic
 * - Redis stream
 * - 파일/오브젝트 스토리지 + 참조 키
 *
 * core 엔진은 저장 방식에 관여하지 않고 "표준 DLQ 메타(DlqMessage)"만 전달합니다.
 */
public interface DlqPublisherPort {

    
    /**
     * 통신 코어 모듈 메시지 또는 이벤트를 발행합니다.
     *
     * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
     * @param dlqMessage 처리할 원본 데이터
     */
    void publish(DlqMessage dlqMessage) throws Exception;
}
