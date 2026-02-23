package com.nori.tc.comm.gateway.context.model;

import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * EquipmentContextRegistry에 적재되는 설비 프로파일 스냅샷입니다.
 *
 * <p>설계 확정사항에 맞춰 tc_eqp 계열 테이블 정보를 하나의 타입으로 묶었습니다.</p>
 * <p>포함 범위:</p>
 * <p>- 정적 설정: tc_eqp, tc_eqp_hsms, tc_eqp_socket, tc_eqp_log, tc_eqp_param</p>
 * <p>- 동적 스냅샷: tc_eqp_state, tc_eqp_port_status</p>
 */
public record EquipmentContextProfile(
        GatewayEquipmentInfo equipmentInfo,
        HsmsSettings hsmsSettings,
        SocketSettings socketSettings,
        CurrentStateSnapshot currentStateSnapshot,
        List<PortStatusSnapshot> portStatuses,
        LogPolicy logPolicy,
        List<ParamSnapshot> params,
        OffsetDateTime loadedAt
) {
    public EquipmentContextProfile {
        Objects.requireNonNull(equipmentInfo, "equipmentInfo is null");
        portStatuses = portStatuses == null ? List.of() : List.copyOf(portStatuses);
        params = params == null ? List.of() : List.copyOf(params);
        loadedAt = loadedAt == null ? OffsetDateTime.now() : loadedAt;
    }

    /**
     * tc_eqp_hsms 스냅샷입니다.
     *
     * <p>SOCKET 설비인 경우 null일 수 있습니다.</p>
     */
    public record HsmsSettings(
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
     * tc_eqp_socket 스냅샷입니다.
     *
     * <p>HSMS 설비인 경우 null일 수 있습니다.</p>
     */
    public record SocketSettings(
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
     * tc_eqp_state 현재 상태 스냅샷입니다.
     *
     * <p>controlState는 시작/중지 판단 기준이 아니라 상태 투영(Projection) 용도로만 사용합니다.</p>
     */
    public record CurrentStateSnapshot(
            String controlState,
            String eqpState,
            OffsetDateTime sinceAt,
            String reasonCode,
            String reasonDetail,
            OffsetDateTime updatedAt
    ) {
    }

    /**
     * tc_eqp_port_status 포트 상태 스냅샷입니다.
     */
    public record PortStatusSnapshot(
            String portId,
            String portType,
            String portState,
            String carrierId,
            String carrierType,
            String carrierState,
            OffsetDateTime updatedAt
    ) {
    }

    /**
     * tc_eqp_log 로그 정책 스냅샷입니다.
     *
     * <p>주의: 애플리케이션 로깅 프레임워크 설정이 아니라 설비별 로그 정책/저장 경로를 의미합니다.</p>
     */
    public record LogPolicy(
            String logLevel,
            Integer logRetentionDays,
            String logPath,
            OffsetDateTime updatedAt
    ) {
    }

    /**
     * tc_eqp_param 파라미터 스냅샷입니다.
     */
    public record ParamSnapshot(
            Long eqpParamKey,
            String paramName,
            String paramVersion,
            String paramValue,
            OffsetDateTime updatedAt
    ) {
    }
}

