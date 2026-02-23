package com.nori.tc.comm.gateway.context.model;

/**
 * 설비 런타임의 현재 실행 상태를 나타내는 열거형입니다.
 *
 * <p>UI 요청/라이프사이클 상태머신/채널 이벤트 처리 결과가 이 상태값으로 수렴됩니다.</p>
 */
public enum EquipmentRuntimeState {

    /**
     * 컨텍스트만 등록되고 아직 연결 시도가 시작되지 않은 상태입니다.
     */
    REGISTERED,

    /**
     * 연결 시도 중(아웃바운드 connect 또는 패시브 리스너 대기 진입 직후) 상태입니다.
     */
    CONNECTING,

    /**
     * 채널 바인딩이 완료되어 통신 가능한 상태입니다.
     */
    CONNECTED,

    /**
     * 종료 요청이 수락되어 정리 작업(채널 종료/리스너 해제 등)을 진행 중인 상태입니다.
     */
    STOPPING,

    /**
     * 처리 중 예외/실패로 운영 개입이 필요할 수 있는 오류 상태입니다.
     */
    ERROR,

    /**
     * 컨텍스트는 유지되지만 채널이 분리된 상태입니다.
     */
    DISCONNECTED,

    /**
     * 컨텍스트/운영 대상에서 삭제된 상태입니다.
     */
    DELETED
}
