package com.nori.tc.comm.socket.socketType;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * socketType -> handler 레지스트리
 *
 * 운영 요구사항
 * - socketType 정의/스크립트가 변경될 수 있으므로,
 *   레지스트리를 “원자적으로 교체(Atomic swap)”하기 쉬운 형태로 유지하는 것이 좋습니다.
 *
 * 이 구현은 간단한 ConcurrentHashMap 기반이며,
 * 필요하면 Immutable snapshot + volatile 참조로 더 깔끔하게 만들 수 있습니다.
 */
public final class SocketTypeRegistry {

    private final Map<String, SocketTypeHandler> handlersByType = new ConcurrentHashMap<>();

    public void register(final SocketTypeHandler handler) {
        Objects.requireNonNull(handler, "handler is null");
        handlersByType.put(handler.socketType(), handler);
    }

    public SocketTypeHandler getRequired(final String socketType) {
        final SocketTypeHandler handler = handlersByType.get(socketType);
        if (handler == null) {
            throw new IllegalArgumentException("No SocketTypeHandler registered for socketType=" + socketType);
        }
        return handler;
    }
}
