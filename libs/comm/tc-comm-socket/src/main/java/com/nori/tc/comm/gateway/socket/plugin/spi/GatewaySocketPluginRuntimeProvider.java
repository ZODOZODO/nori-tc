package com.nori.tc.comm.gateway.socket.plugin.spi;

import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeHandler;

import java.util.Optional;

/**
 * 설비별 SOCKET 플러그인 핸들러 조회 포트입니다.
 *
 * <p>목적:</p>
 * <p>1) 코어/파이프라인은 "플러그인 핸들러를 어디서 읽는지"를 몰라도 됩니다.</p>
 * <p>2) DB/JAR/ClassLoader 등 기술 세부사항은 어댑터에서만 관리합니다.</p>
 * <p>3) 핸들러가 없으면 Optional.empty()를 반환하여 기본 registry로 fallback 합니다.</p>
 */
@FunctionalInterface
public interface GatewaySocketPluginRuntimeProvider {

    /**
     * eqpId 기준으로 현재 활성 SOCKET 플러그인 핸들러를 조회합니다.
     *
     * @param eqpId 설비 ID
     * @return 플러그인 핸들러(Optional)
     */
    Optional<SocketTypeHandler> findByEqpId(String eqpId);

    /**
     * 플러그인 런타임이 구성되지 않은 환경에서 사용할 no-op provider 입니다.
     *
     * @return 항상 empty 를 반환하는 provider
     */
    static GatewaySocketPluginRuntimeProvider noop() {
        return eqpId -> Optional.empty();
    }
}

