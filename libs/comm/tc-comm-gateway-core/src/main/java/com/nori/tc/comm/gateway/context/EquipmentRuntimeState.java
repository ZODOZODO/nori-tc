package com.nori.tc.comm.gateway.context;

/**
 * 설비 컨텍스트의 실제 런타임 상태(Actual State)입니다.
 *
 * <p>Netty 채널 바인딩/언바인딩, UI START/END 요청 결과를 반영하는 현재 상태입니다.
 * Desired State와 조합해서 "수렴 중인지", "안정 상태인지"를 판단할 수 있습니다.</p>
 */
public enum EquipmentRuntimeState {

    /**
     * 컨텍스트는 존재하지만 아직 연결 시도가 진행되지 않은 상태입니다.
     */
    REGISTERED,

    /**
     * 연결 시도를 진행 중인 상태입니다.
     */
    CONNECTING,

    /**
     * 채널이 정상 바인딩되어 송수신 가능한 상태입니다.
     */
    CONNECTED,

    /**
     * 채널이 종료되어 송수신 불가한 상태입니다.
     */
    DISCONNECTED,

    /**
     * 컨텍스트가 삭제되어 추적 대상에서 빠진 상태입니다.
     */
    DELETED
}

