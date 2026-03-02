package com.nori.tc.messaging.kafka.contract;

/**
 * Gateway/Business Core → UI 작업 응답 메시지의 이벤트 타입 목록입니다.
 *
 * <p>Gateway와 Business Core가 {@code tc.ui.commands} 토픽에 발행하는
 * reply 메시지의 {@code metadata.eventType} 값을 enum으로 정규화합니다.
 * 문자열 직접 비교로 인한 오탈자와 분기 누락을 방지하기 위한 목적입니다.</p>
 *
 * <p>명명 규칙: 요청 eventType({@link KafkaUiTaskEventType})에 {@code _REP} 접미사를 붙입니다.</p>
 *
 * <pre>
 * 요청 eventType          → 응답 replyEventType
 * ─────────────────────────────────────────────
 * EQP_CREATE             → EQP_CREATE_REP
 * EQP_UPDATE             → EQP_UPDATE_REP
 * EQP_DELETE             → EQP_DELETE_REP
 * EQP_START              → EQP_START_REP  (lifecycle 완료 후 지연 응답)
 * EQP_END                → EQP_END_REP    (lifecycle 완료 후 지연 응답)
 * </pre>
 *
 * <p>EQP_CREATE_REP / EQP_UPDATE_REP / EQP_DELETE_REP는 Gateway와 Business Core
 * 양쪽에서 각각 발행됩니다. EQP_START_REP / EQP_END_REP는 Gateway 단일 발행입니다.</p>
 */
public enum KafkaUiTaskReplyEventType {

    /**
     * EQP_CREATE 요청에 대한 응답입니다.
     * Gateway와 Business Core 양쪽에서 각각 발행됩니다.
     */
    EQP_CREATE_REP,

    /**
     * EQP_UPDATE 요청에 대한 응답입니다.
     * Gateway와 Business Core 양쪽에서 각각 발행됩니다.
     */
    EQP_UPDATE_REP,

    /**
     * EQP_DELETE 요청에 대한 응답입니다.
     * Gateway와 Business Core 양쪽에서 각각 발행됩니다.
     */
    EQP_DELETE_REP,

    /**
     * EQP_START 요청에 대한 응답입니다.
     * Gateway 단일 발행이며, lifecycle 상태머신 완료 후 지연 발행됩니다.
     */
    EQP_START_REP,

    /**
     * EQP_END 요청에 대한 응답입니다.
     * Gateway 단일 발행이며, lifecycle 상태머신 완료 후 지연 발행됩니다.
     */
    EQP_END_REP;

    /**
     * 문자열 이벤트 타입을 enum 값으로 변환합니다.
     *
     * <p>대소문자를 무시하며, 전후 공백을 제거한 후 매핑합니다.</p>
     *
     * @param text 입력 문자열 이벤트 타입 (예: "EQP_CREATE_REP")
     * @return 정규화된 enum 값
     * @throws IllegalArgumentException text가 null, 공백, 또는 알 수 없는 값인 경우
     */
    public static KafkaUiTaskReplyEventType fromText(final String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("reply eventType is required");
        }
        try {
            return KafkaUiTaskReplyEventType.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported reply eventType: " + text, ex);
        }
    }
}
