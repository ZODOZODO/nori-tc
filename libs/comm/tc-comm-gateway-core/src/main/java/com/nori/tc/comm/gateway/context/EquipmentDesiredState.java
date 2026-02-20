package com.nori.tc.comm.gateway.context;

/**
 * 장비 컨텍스트의 목표 상태(Desired State)입니다.
 *
 * <p>실제 연결 상태(connected/disconnected)와 분리해
 * "장비를 어떤 상태로 유지할지"를 표현합니다.</p>
 */
public enum EquipmentDesiredState {

    /**
     * 장비를 운영 상태로 유지하려는 목표입니다.
     *
     * <p>- 설비 ACTIVE  : 게이트웨이 서버 수신 허용 상태</p>
     * <p>- 설비 PASSIVE : 게이트웨이 아웃바운드 connect/reconnect 대상 상태</p>
     */
    STARTED,

    /**
     * 장비를 중지 상태로 유지하려는 목표입니다.
     *
     * <p>- 자동 아웃바운드 재연결 금지</p>
     * <p>- 기존 채널 종료 대상</p>
     */
    ENDED,

    /**
     * 메모리 컨텍스트에서 제거된 상태입니다.
     */
    DELETED
}
