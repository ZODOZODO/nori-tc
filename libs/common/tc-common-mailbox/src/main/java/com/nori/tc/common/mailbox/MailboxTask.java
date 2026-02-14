package com.nori.tc.common.mailbox;

/**
 * Mailbox 스케줄러에서 처리할 작업의 최소 계약입니다.
 *
 * <p>공통 모듈은 도메인 세부 정보(메시지 타입, payload 구조)를 몰라야 하므로,
 * 라우팅 키(예: eqpId)만 필수 계약으로 강제합니다.</p>
 */
public interface MailboxTask {

    /**
     * 순차 처리 단위를 식별하는 라우팅 키를 반환합니다.
     *
     * @return 라우팅 키(예: eqpId)
     */
    String routingKey();
}
