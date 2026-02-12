package com.nori.tc.comm.gateway.socket.socketType;

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

    
    /**
     * 소켓 통신 모듈 데이터의 저장/갱신을 처리합니다.
     *
     * <p>소켓 타입 분기, 인코딩/디코딩, 연결 상태 관리를 기준으로 동작합니다.</p>
     * @param handler 소켓 통신 모듈 처리에 사용하는 입력 값
     */
    public void register(final SocketTypeHandler handler) {
        Objects.requireNonNull(handler, "handler is null");
        final String socketType = Objects.requireNonNull(handler.socketType(), "socketType is null");
        if (socketType.isBlank()) {
            throw new IllegalArgumentException("socketType is blank");
        }
        handlersByType.put(socketType, handler);
    }

    
    /**
     * 소켓 통신 모듈의 현재 값을 조회합니다.
     *
     * <p>소켓 타입 분기, 인코딩/디코딩, 연결 상태 관리를 기준으로 동작합니다.</p>
     * @param socketType 통신 채널/세션 정보
     * @return 소켓 통신 모듈 처리 결과
     */
    public SocketTypeHandler getRequired(final String socketType) {
        if (socketType == null || socketType.isBlank()) {
            throw new IllegalArgumentException("socketType is required");
        }
        final SocketTypeHandler handler = handlersByType.get(socketType);
        if (handler == null) {
            throw new IllegalArgumentException("No SocketTypeHandler registered for socketType=" + socketType);
        }
        return handler;
    }
}
