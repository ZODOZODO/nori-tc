package com.nori.tc.comm.gateway.context;

/**
 * EquipmentRuntimeState 열거형입니다.
 *
 * <p>해당 모듈에서 공통 계약과 동작 경계를 정의하며,
 * 호출 계층에서 일관된 사용이 가능하도록 설계되었습니다.</p>
 */
public enum EquipmentRuntimeState {

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    REGISTERED,

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    CONNECTING,

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    CONNECTED,

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    STOPPING,

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    ERROR,

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    DISCONNECTED,

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    DELETED
}
