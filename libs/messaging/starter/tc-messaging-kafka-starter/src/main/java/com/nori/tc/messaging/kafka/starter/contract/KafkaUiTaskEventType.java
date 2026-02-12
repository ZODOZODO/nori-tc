package com.nori.tc.messaging.kafka.starter.contract;

/**
 * UI -> Gateway 런타임 제어 이벤트 타입 목록입니다.
 *
 * <p>UI 백엔드가 gateway에 전달하는 task 메시지의 {@code metadata.eventType}과
 * 1:1로 매핑됩니다.</p>
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
     * 문자열 eventType을 enum으로 변환합니다.
     *
     * @param text 수신한 eventType 문자열
     * @return 파싱된 이벤트 타입
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
