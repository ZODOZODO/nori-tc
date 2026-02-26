package com.nori.tc.messaging.kafka.contract;

import java.util.List;

/**
 * UI -> Gateway EQP_CREATE/EQP_UPDATE 요청 시 전달하는 설비 프로파일 스냅샷 계약입니다.
 *
 * <p>Gateway가 DB 재조회 없이 EQP bean(EquipmentContextRegistry)을 갱신할 수 있도록,
 * 런타임 검증과 컨텍스트 구성에 필요한 정보를 하나의 payload로 전달합니다.</p>
 *
 * <p>주의:</p>
 * <p>- 본 타입은 계약 계층이므로 enum 대신 문자열을 사용합니다.</p>
 * <p>- 날짜/시각 값도 JSON 직렬화 호환성을 위해 ISO-8601 문자열로 전달합니다.</p>
 * <p>- 실제 필수값 검증과 의미 검증은 Gateway 처리 계층에서 수행합니다.</p>
 */
public record GatewayEquipmentProfileSnapshot(
        Long eqpKey,
        String eqpId,
        String commInterfaceType,
        String socketType,
        Integer hsmsDeviceId,
        String eqpIp,
        Integer eqpPort,
        Long modelKey,
        String connectionMode,
        Integer routePartition,
        Boolean enabled,
        HsmsSettingsSnapshot hsmsSettings,
        SocketSettingsSnapshot socketSettings,
        CurrentStateSnapshot currentStateSnapshot,
        List<PortStatusSnapshot> portStatuses,
        LogPolicySnapshot logPolicy,
        List<ParamSnapshot> params,
        String updatedAt
) {

    /**
     * 리스트 필드를 null-safe/불변 리스트로 보정합니다.
     */
    public GatewayEquipmentProfileSnapshot {
        portStatuses = portStatuses == null ? List.of() : List.copyOf(portStatuses);
        params = params == null ? List.of() : List.copyOf(params);
    }

    /**
     * HSMS 설정 스냅샷입니다.
     */
    public record HsmsSettingsSnapshot(
            Integer deviceId,
            String connectionMode,
            Integer t3Timeout,
            Integer t5Timeout,
            Integer t6Timeout,
            Integer t7Timeout,
            Integer t8Timeout,
            Boolean linkTestEnabled,
            Integer linkTestInterval,
            Long maxMsgBytes
    ) {
    }

    /**
     * SOCKET 설정 스냅샷입니다.
     */
    public record SocketSettingsSnapshot(
            String socketProtocolType,
            String connectionMode,
            String charset,
            Boolean heartbeatEnabled,
            Integer heartbeatInterval,
            Integer readTimeout,
            Integer writeTimeout,
            Integer maxFrameSizeBytes,
            Boolean keepAliveEnabled
    ) {
    }

    /**
     * 현재 상태 스냅샷입니다.
     */
    public record CurrentStateSnapshot(
            String controlState,
            String eqpState,
            String sinceAt,
            String reasonCode,
            String reasonDetail,
            String updatedAt
    ) {
    }

    /**
     * 포트 상태 스냅샷입니다.
     */
    public record PortStatusSnapshot(
            String portId,
            String portType,
            String portState,
            String carrierId,
            String carrierType,
            String carrierState,
            String updatedAt
    ) {
    }

    /**
     * 설비 로그 정책 스냅샷입니다.
     */
    public record LogPolicySnapshot(
            String logLevel,
            Integer logRetentionDays,
            String logPath,
            String updatedAt
    ) {
    }

    /**
     * 설비 파라미터 스냅샷입니다.
     */
    public record ParamSnapshot(
            Long eqpParamKey,
            String paramName,
            String paramVersion,
            String paramValue,
            String updatedAt
    ) {
    }
}
