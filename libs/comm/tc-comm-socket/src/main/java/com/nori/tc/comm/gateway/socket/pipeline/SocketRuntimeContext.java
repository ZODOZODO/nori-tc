package com.nori.tc.comm.gateway.socket.pipeline;

import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.gateway.socket.config.SocketTypeConfig;
import com.nori.tc.comm.gateway.socket.socketType.SocketTypeRegistry;

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
     * 소켓 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>소켓 타입 분기, 인코딩/디코딩, 연결 상태 관리를 기준으로 동작합니다.</p>
     * @return 소켓 통신 모듈 처리 결과
     */
    SocketTypeConfig socketTypeConfig();

    
    /**
     * 소켓 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>소켓 타입 분기, 인코딩/디코딩, 연결 상태 관리를 기준으로 동작합니다.</p>
     * @return 소켓 통신 모듈 처리 결과
     */
    SocketTypeRegistry socketTypeRegistry();
}
