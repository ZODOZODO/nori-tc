package com.nori.tc.comm.adapters.kafka.messaging.ui;

/**
 * UI task 처리 중 비즈니스 검증 실패를 표현하는 예외입니다.
 *
 * <p>응답 메시지의 ERRORCODE/ERRORMSG로 바로 매핑할 수 있도록
 * errorCode 필드를 함께 보관합니다.</p>
 */
public class GatewayUiTaskProcessingException extends RuntimeException {

    private final String errorCode;

    /**
     * 에러코드와 메시지를 함께 받는 생성자입니다.
     */
    public GatewayUiTaskProcessingException(final String errorCode, final String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * UI 응답에 사용할 에러코드를 반환합니다.
     */
    public String errorCode() {
        return errorCode;
    }
}
