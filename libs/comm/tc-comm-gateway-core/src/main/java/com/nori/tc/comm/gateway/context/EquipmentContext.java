package com.nori.tc.comm.gateway.context;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 설비 1대의 인메모리 실행 컨텍스트입니다.
 *
 * <p>정적 프로파일(EquipmentContextProfile) + 동적 런타임 상태를 한 객체에서 관리합니다.</p>
 * <p>멀티스레드 접근을 고려해 상태 변경 메서드는 synchronized로 직렬화합니다.</p>
 */
public final class EquipmentContext {

    private final String eqpId;

    private EquipmentContextProfile profile;
    private EquipmentDesiredState desiredState;
    private EquipmentRuntimeState runtimeState;
    private String lastEventType;
    private String lastTraceId;
    private OffsetDateTime updatedAt;

    /**
     * 컨텍스트를 생성합니다.
     *
     * @param profile 최초 적재 프로파일
     * @param desiredState 최초 목표 상태
     * @param runtimeState 최초 실제 상태
     * @param eventType 상태를 만든 이벤트 타입
     * @param traceId 상태를 만든 트레이스 ID
     */
    public EquipmentContext(
            final EquipmentContextProfile profile,
            final EquipmentDesiredState desiredState,
            final EquipmentRuntimeState runtimeState,
            final String eventType,
            final String traceId
    ) {
        this.profile = Objects.requireNonNull(profile, "profile is null");
        this.eqpId = profile.equipmentInfo().equipmentId();
        this.desiredState = Objects.requireNonNull(desiredState, "desiredState is null");
        this.runtimeState = Objects.requireNonNull(runtimeState, "runtimeState is null");
        this.lastEventType = eventType;
        this.lastTraceId = traceId;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 최신 프로파일로 교체합니다.
     */
    public synchronized void refreshProfile(
            final EquipmentContextProfile nextProfile,
            final String eventType,
            final String traceId
    ) {
        this.profile = Objects.requireNonNull(nextProfile, "nextProfile is null");
        this.lastEventType = eventType;
        this.lastTraceId = traceId;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 목표 상태를 변경합니다.
     */
    public synchronized void updateDesiredState(
            final EquipmentDesiredState nextDesiredState,
            final String eventType,
            final String traceId
    ) {
        this.desiredState = Objects.requireNonNull(nextDesiredState, "nextDesiredState is null");
        this.lastEventType = eventType;
        this.lastTraceId = traceId;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 실제 런타임 상태를 변경합니다.
     */
    public synchronized void updateRuntimeState(
            final EquipmentRuntimeState nextRuntimeState,
            final String eventType,
            final String traceId
    ) {
        this.runtimeState = Objects.requireNonNull(nextRuntimeState, "nextRuntimeState is null");
        this.lastEventType = eventType;
        this.lastTraceId = traceId;
        this.updatedAt = OffsetDateTime.now();
    }

    public synchronized String eqpId() {
        return eqpId;
    }

    public synchronized EquipmentContextProfile profile() {
        return profile;
    }

    public synchronized EquipmentDesiredState desiredState() {
        return desiredState;
    }

    public synchronized EquipmentRuntimeState runtimeState() {
        return runtimeState;
    }

    public synchronized String lastEventType() {
        return lastEventType;
    }

    public synchronized String lastTraceId() {
        return lastTraceId;
    }

    public synchronized OffsetDateTime updatedAt() {
        return updatedAt;
    }
}

