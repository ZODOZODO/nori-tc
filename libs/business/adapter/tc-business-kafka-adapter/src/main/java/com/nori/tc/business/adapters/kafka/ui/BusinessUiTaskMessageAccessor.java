package com.nori.tc.business.adapters.kafka.ui;

import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskMessageAccessor;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.springframework.stereotype.Component;

/**
 * BusinessUiTaskMessageAccessor 클래스입니다.
 *
 * <p>해당 모듈에서 공통 계약과 동작 경계를 정의하며,
 * 호출 계층에서 일관된 사용이 가능하도록 설계되었습니다.</p>
 */
@Component
public class BusinessUiTaskMessageAccessor implements KafkaTaskMessageAccessor<KafkaUiTaskMessage> {

    /**
     * eventType 기능을 수행합니다.
     *
     * @param request 입력 값
     * @return 처리 결과
     */

    @Override
    public String eventType(final KafkaUiTaskMessage request) {
        if (request == null || request.metadata() == null) {
            return null;
        }
        return request.metadata().eventType();
    }

    /**
     * traceId 기능을 수행합니다.
     *
     * @param request 입력 값
     * @return 처리 결과
     */

    @Override
    public String traceId(final KafkaUiTaskMessage request) {
        if (request == null || request.metadata() == null) {
            return null;
        }
        return request.metadata().traceId();
    }

    /**
     * eqpId 기능을 수행합니다.
     *
     * @param request 입력 값
     * @return 처리 결과
     */

    @Override
    public String eqpId(final KafkaUiTaskMessage request) {
        if (request == null || request.data() == null) {
            return null;
        }
        return request.data().eqpId();
    }
}



