package com.nori.tc.messaging.domain;

/**
 * Messaging Domain 모듈 마커 클래스입니다.
 *
 * <p>용도:
 * 1) 모듈 경계 확인
 * 2) 패키지 스캔 기준점 제공
 * 3) 공통 계약(토픽/Envelope/검증 정책) 위치 식별</p>
 */
public final class MessagingDomainMarker {

    /**
     * 유틸리티 클래스 인스턴스화를 방지합니다.
     */
    private MessagingDomainMarker() {
    }
}
