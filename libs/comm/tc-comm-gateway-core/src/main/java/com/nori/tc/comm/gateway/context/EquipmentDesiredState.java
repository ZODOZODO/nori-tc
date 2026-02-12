package com.nori.tc.comm.gateway.context;

/**
 * 설비 컨텍스트의 목표 상태(Desired State)입니다.
 *
 * <p>UI 명령 또는 부팅 정책에 의해 "설비를 어떤 상태로 유지하고 싶은지"를 표현합니다.
 * 실제 연결 상태(connected/disconnected)와 분리하여 관리해야 상태 수렴 제어가 단순해집니다.</p>
 */
public enum EquipmentDesiredState {

    /**
     * 설비를 운전 상태로 유지하려는 목표입니다.
     * ACTIVE는 connect/reconnect 대상, PASSIVE는 수신 허용 대상으로 해석합니다.
     */
    STARTED,

    /**
     * 설비를 중지 상태로 유지하려는 목표입니다.
     * ACTIVE 재연결 금지, 기존 채널 종료 대상으로 해석합니다.
     */
    ENDED,

    /**
     * 메모리 컨텍스트에서 제거된 상태입니다.
     * 재사용하려면 CREATE/UPDATE(재적재)가 선행되어야 합니다.
     */
    DELETED
}

