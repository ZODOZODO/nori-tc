package com.nori.tc.messaging.domain;

/**
 * Messaging domain marker.
 *
 * - 모듈 스캔 기준점 또는 의존성 가시성 확인 용도
 */
public final class MessagingDomainMarker {

    /**
     * 메시징 도메인 모듈 구성 요소를 초기화합니다.
     *
     * <p>도메인 경계 식별과 모듈 계약의 불변식을 기준으로 처리합니다.</p>
     */
    private MessagingDomainMarker() {
    }
}
