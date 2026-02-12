package com.nori.tc.comm.core.port;

import com.nori.tc.comm.domain.dlq.DlqMessage;

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

    void publish(DlqMessage dlqMessage) throws Exception;
}
