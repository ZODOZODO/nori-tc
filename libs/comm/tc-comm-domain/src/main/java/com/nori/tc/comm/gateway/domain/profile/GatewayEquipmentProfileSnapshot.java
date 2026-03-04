package com.nori.tc.comm.gateway.domain.profile;

import java.util.List;

/**
 * UI -> Gateway EQP_CREATE/EQP_UPDATE 요청 시 전달하는 설비 프로파일 스냅샷 도메인 계약입니다.
 *
 * <p>이 타입은 Kafka 전용 계약이 아니라 "설비 도메인 스냅샷" 자체를 표현하므로,
 * Kafka 계약 모듈이 아닌 comm-domain 계층에 위치합니다.</p>
 *
 * <p>설계 원칙:</p>
 * <ul>
 *   <li>기술 중립성: 특정 메시징 구현(Kafka)에 종속되지 않습니다.</li>
 *   <li>직렬화 호환성: enum 대신 문자열, 시각은 ISO-8601 문자열을 사용합니다.</li>
 *   <li>불변 컬렉션: 리스트 필드는 null-safe + 방어적 복사로 고정합니다.</li>
 * </ul>
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
