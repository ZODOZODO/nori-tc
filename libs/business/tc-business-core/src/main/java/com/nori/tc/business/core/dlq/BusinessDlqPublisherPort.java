package com.nori.tc.business.core.dlq;

import com.nori.tc.business.domain.dlq.BusinessDlqMessage;

/**
 * Business DLQ 발행 포트입니다.
 *
 * <p>핵심 의도:</p>
 * <p>- 코어는 "DLQ를 언제 발행해야 하는지"만 책임지고,</p>
 * <p>- 저장 위치(Redis/DB/Kafka/ObjectStorage)는 어댑터가 책임집니다.</p>
 */
@FunctionalInterface
public interface BusinessDlqPublisherPort {

    /**
     * DLQ 메시지를 발행합니다.
     *
     * @param dlqMessage DLQ 표준 메시지
     * @throws Exception 저장소 오류 등 발행 실패 예외
     */
    void publish(BusinessDlqMessage dlqMessage) throws Exception;

    /**
     * 기본 no-op 구현을 제공합니다.
     *
     * @return 아무 동작을 하지 않는 포트 구현
     */
    static BusinessDlqPublisherPort noop() {
        return message -> {
            // 의도적으로 no-op 처리합니다.
        };
    }
}
