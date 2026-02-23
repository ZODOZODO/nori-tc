package com.nori.tc.comm.gateway.socket.runtime;

import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.gateway.socket.config.SocketTypeConfig;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeRegistry;

/**
 * SOCKET 런타임 컨텍스트(확장 인터페이스)
 *
 * 이유
 * - core의 EquipmentRuntimeContext는 공통만 제공하고,
 * - SOCKET 전용 구성(socketTypeConfig, socketTypeRegistry 등)은 이 확장 인터페이스로 노출합니다.
 *
 * 앱 레이어 구현
 * - SOCKET 설비의 ctx 구현체는 이 인터페이스를 구현해야 합니다.
 */
public interface SocketRuntimeContext extends EquipmentRuntimeContext {

    /**
     * 현재 설비에 적용되는 socketType 설정을 반환합니다.
     *
     * <p>SOCKET 파이프라인은 이 설정을 사용해 프레임 길이 제한, 빈 프레임 허용 여부, 타입명 등을 결정합니다.</p>
     *
     * @return 설비별 socketType 설정
     */
    SocketTypeConfig socketTypeConfig();

    /**
     * socketType 핸들러 조회 레지스트리를 반환합니다.
     *
     * <p>파이프라인은 이 레지스트리를 통해 설비별 socketType에 맞는 핸들러를 조회합니다.</p>
     *
     * @return socketType 핸들러 레지스트리
     */
    SocketTypeRegistry socketTypeRegistry();
}
