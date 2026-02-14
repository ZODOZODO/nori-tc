package com.nori.tc.business.core.workflow;

import com.nori.tc.business.domain.runtime.BusinessInboundRecord;

import java.util.Map;
import java.util.Objects;

/**
 * workflow_filter 평가 시 사용하는 컨텍스트 모델입니다.
 *
 * <p>필터 평가는 입력 데이터를 아래 두 축으로 분리해 조회합니다.</p>
 * <p>1) MSG: payload(JSON)에서 파싱한 메시지 변수 맵</p>
 * <p>2) CTX: topic/partition/offset/eqpId 같은 런타임 컨텍스트 맵</p>
 */
public record BusinessWorkflowFilterContext(
        BusinessInboundRecord record,
        Map<String, Object> messageVariables,
        Map<String, Object> contextVariables
) {

    /**
     * record/변수 맵의 null 여부를 검증하고, 외부 변경 영향을 막기 위해 불변 복사합니다.
     */
    public BusinessWorkflowFilterContext {
        Objects.requireNonNull(record, "record is null");
        messageVariables = Map.copyOf(Objects.requireNonNull(messageVariables, "messageVariables is null"));
        contextVariables = Map.copyOf(Objects.requireNonNull(contextVariables, "contextVariables is null"));
    }
}


