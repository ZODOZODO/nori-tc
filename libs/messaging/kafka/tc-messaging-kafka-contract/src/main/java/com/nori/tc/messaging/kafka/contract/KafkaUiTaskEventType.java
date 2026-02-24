package com.nori.tc.messaging.kafka.contract;

/**
 * UI -> Gateway 작업 요청 메시지의 이벤트 타입 목록입니다.
 *
 * <p>UI에서 전달하는 {@code metadata.eventType} 값을 enum으로 정규화하여
 * 문자열 비교 오탈자와 분기 누락을 줄이기 위한 목적입니다.</p>
 */
public enum KafkaUiTaskEventType {
    EQP_CREATE,
    EQP_UPDATE,
    EQP_DELETE,
    EQP_START,
    EQP_END,
    EQP_SEND_MESSAGE,
    EQP_UPDATE_JARFILE;

    /**
     * 문자열 이벤트 타입을 enum 값으로 변환합니다.
     *
     * @param text 입력 문자열 이벤트 타입
     * @return 정규화된 enum 값
     */
    public static KafkaUiTaskEventType fromText(final String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        try {
            return KafkaUiTaskEventType.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported eventType: " + text, ex);
        }
    }
}
