package com.nori.tc.comm.socket.socketType.spi;

import com.nori.tc.comm.socket.socketType.SocketTypeHandler;

import java.util.Optional;

/**
 * 스크립트 엔진 미사용(No-op) 구현
 *
 * - 스크립트를 도입하기 전 단계에서도, wiring이 깨지지 않게 하기 위한 기본 구현체입니다.
 */
public final class NoopSocketTypeScriptEngine implements SocketTypeScriptEngine {

    @Override
    public Optional<SocketTypeHandler> tryLoad(final String socketType, final String version) {
        return Optional.empty();
    }
}
