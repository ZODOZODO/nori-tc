package com.nori.tc.business.core.workflow.api.match;

import com.nori.tc.business.domain.runtime.BusinessInboundRecord;

import java.util.Map;
import java.util.Objects;

/**
 * workflow_filter 평가에 사용하는 컨텍스트 모델입니다.
 *
 * <p>MSG(payload 기반 변수)와 CTX(런타임 문맥 변수)를 분리해 제공합니다.</p>
 */
public record BusinessWorkflowFilterContext(
        BusinessInboundRecord record,
        Map<String, Object> messageVariables,
        Map<String, Object> contextVariables
) {

    /**
     * null 여부를 검증하고 변수 맵은 불변 복사합니다.
     */
    public BusinessWorkflowFilterContext {
        Objects.requireNonNull(record, "record is null");
        messageVariables = Map.copyOf(Objects.requireNonNull(messageVariables, "messageVariables is null"));
        contextVariables = Map.copyOf(Objects.requireNonNull(contextVariables, "contextVariables is null"));
    }
}