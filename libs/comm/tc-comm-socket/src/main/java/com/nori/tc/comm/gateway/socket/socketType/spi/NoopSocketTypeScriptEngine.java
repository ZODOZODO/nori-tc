package com.nori.tc.comm.gateway.socket.socketType.spi;

import java.util.Optional;

import com.nori.tc.comm.gateway.socket.socketType.SocketTypeHandler;

/**
 * 스크립트 엔진 미사용(No-op) 구현
 *
 * - 스크립트를 도입하기 전 단계에서도, wiring이 깨지지 않게 하기 위한 기본 구현체입니다.
 */
public final class NoopSocketTypeScriptEngine implements SocketTypeScriptEngine {

    
    /**
     * 소켓 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>소켓 타입 분기, 인코딩/디코딩, 연결 상태 관리를 기준으로 동작합니다.</p>
     * @param socketType 통신 채널/세션 정보
     * @param version 소켓 통신 모듈 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    public Optional<SocketTypeHandler> tryLoad(final String socketType, final String version) {
        return Optional.empty();
    }
}
