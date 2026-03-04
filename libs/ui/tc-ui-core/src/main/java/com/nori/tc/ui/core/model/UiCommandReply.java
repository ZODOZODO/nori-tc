package com.nori.tc.ui.core.model;

import com.nori.tc.ui.domain.task.UiTaskStatus;

/**
 * Gateway/Business -> UI Backend 수신 응답의 기술 중립 DTO입니다.
 *
 * <p>Kafka 계약 타입을 core 포트 시그니처에서 제거하기 위해 정의했습니다.</p>
 *
 * @param traceId 요청/응답 상관관계 추적 ID
 * @param source 응답 출처(예: TC-COMM-GATEWAY, TC-BUSINESS-CORE)
 * @param eventType 응답 이벤트 타입(예: EQP_CREATE_REP)
 * @param eqpId 설비 ID
 * @param interfaceType 인터페이스 타입
 * @param status 처리 상태(PASS/FAIL)
 * @param errorCode 실패 코드(성공 시 null)
 * @param errorMsg 실패 메시지(성공 시 null)
 */
public record UiCommandReply(
        String traceId,
        String source,
        String eventType,
        String eqpId,
        String interfaceType,
        UiTaskStatus status,
        String errorCode,
        String errorMsg
) {

    /**
     * 필수값을 검증합니다.
     */
    public UiCommandReply {
        requireText("traceId", traceId);
        requireText("source", source);
        requireText("eventType", eventType);
        requireText("eqpId", eqpId);
        requireText("interfaceType", interfaceType);
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
    }

    private static void requireText(final String fieldName, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
