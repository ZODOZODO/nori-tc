package com.nori.tc.comm.gateway.socket.plugin.spi;

/**
 * 설비별 SOCKET 플러그인 런타임 갱신 포트입니다.
 *
 * <p>주요 호출 경로:</p>
 * <p>- UI 이벤트(EQP_UPDATE_JARFILE) 처리 후 특정 설비 런타임 재로딩</p>
 * <p>- 운영 중 플러그인 JAR 교체 시 핫스왑(원자 교체) 수행</p>
 */
@FunctionalInterface
public interface GatewaySocketPluginRuntimeMutationPort {

    /**
     * 특정 설비의 플러그인 런타임을 재로딩합니다.
     *
     * @param eqpId 설비 ID
     */
    void reloadByEqpId(String eqpId);

    /**
     * 플러그인 런타임이 없는 환경에서 사용할 no-op mutation port 입니다.
     *
     * @return 아무 동작도 하지 않는 구현체
     */
    static GatewaySocketPluginRuntimeMutationPort noop() {
        return eqpId -> {
            // no-op
        };
    }
}

