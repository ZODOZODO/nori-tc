package com.nori.tc.ui.core.exception;

/**
 * 설비 파라미터가 이미 다른 사용자에 의해 체크아웃된 상태에서 체크아웃을 시도할 때 발생하는 예외입니다.
 *
 * <p>처리 정책:</p>
 * <p>Web Adapter(Controller)에서 본 예외를 수신하면 HTTP 409 Conflict로 변환합니다.</p>
 */
public class EqpAlreadyCheckedOutException extends UiConflictException {

    /**
     * 체크아웃 중인 사용자 ID를 포함한 409 예외를 생성합니다.
     *
     * @param eqpId 설비 비즈니스 ID
     * @param checkedOutBy 현재 체크아웃 중인 사용자 ID
     */
    public EqpAlreadyCheckedOutException(final String eqpId, final String checkedOutBy) {
        super("설비 파라미터가 이미 체크아웃 중입니다. eqpId=" + eqpId + ", checkedOutBy=" + checkedOutBy);
    }

    /**
     * 메시지만 포함한 409 예외를 생성합니다.
     *
     * @param message 충돌 원인 메시지
     */
    public EqpAlreadyCheckedOutException(final String message) {
        super(message);
    }

    /**
     * 원인 예외를 포함한 409 예외를 생성합니다.
     *
     * @param message 충돌 원인 메시지
     * @param cause 원인 예외
     */
    public EqpAlreadyCheckedOutException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
