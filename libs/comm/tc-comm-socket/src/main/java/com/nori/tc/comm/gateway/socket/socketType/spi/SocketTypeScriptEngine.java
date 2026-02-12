package com.nori.tc.comm.gateway.socket.socketType.spi;

import java.util.Optional;

import com.nori.tc.comm.gateway.socket.socketType.SocketTypeHandler;

/**
 * socketType 동적 스크립트 엔진 SPI
 *
 * 목적
 * - socketType의 encode/decode 규칙이 자주 바뀌므로, “코드 배포 없이” 교체할 수 있는 확장 포인트를 둡니다.
 * - 실제 스크립트 런타임(JS/GraalVM 등)은 앱 레이어에서 선택/주입하세요.
 *
 * 운영 안전 장치(권장)
 * - 로드 성공 시에만 Atomic swap
 * - 실행 시간 제한(타임아웃)
 * - 실패 시 DLQ + quarantine(폭주 방지)
 */
public interface SocketTypeScriptEngine {

    /**
     * socketType에 해당하는 핸들러를 동적으로 로드합니다.
     *
     * @param socketType socket_protocol_type 값
     * @param version 스크립트/규칙 버전(옵션)
     * @return 로드 성공 시 handler, 없으면 empty
     */
    Optional<SocketTypeHandler> tryLoad(String socketType, String version);
}
