package com.nori.tc.comm.gateway.context.model;

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
        // 프로파일은 장비 통신 설정/정책의 최신 스냅샷이므로 원자적으로 교체합니다.
        this.profile = Objects.requireNonNull(nextProfile, "nextProfile is null");
        // 어떤 요청이 컨텍스트를 갱신했는지 추적 가능하도록 메타 정보를 함께 기록합니다.
        this.lastEventType = eventType;
        this.lastTraceId = traceId;
        // 상태 변경/프로파일 갱신 공통 기준 시각을 업데이트합니다.
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
        // 운영자/상위 시스템의 목표 상태 변경 요청을 반영합니다.
        this.desiredState = Objects.requireNonNull(nextDesiredState, "nextDesiredState is null");
        // 후속 진단 시 어떤 이벤트가 상태를 바꿨는지 식별할 수 있어야 합니다.
        this.lastEventType = eventType;
        this.lastTraceId = traceId;
        // desired state 변경도 컨텍스트 변경 이력으로 간주합니다.
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
        // 채널/라이프사이클 처리 결과로 확정된 실제 런타임 상태를 반영합니다.
        this.runtimeState = Objects.requireNonNull(nextRuntimeState, "nextRuntimeState is null");
        // 상태 전이 원인 추적을 위해 이벤트 타입과 traceId를 함께 저장합니다.
        this.lastEventType = eventType;
        this.lastTraceId = traceId;
        // 런타임 상태 전이 시각을 최신화합니다.
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 컨텍스트가 관리하는 설비 ID를 반환합니다.
     *
     * @return 설비 ID
     */
    public synchronized String eqpId() {
        return eqpId;
    }

    /**
     * 최신 설비 프로파일 스냅샷을 반환합니다.
     *
     * @return 설비 컨텍스트 프로파일
     */
    public synchronized EquipmentContextProfile profile() {
        return profile;
    }

    /**
     * 운영자가 요청한 목표 상태(desired state)를 반환합니다.
     *
     * @return 목표 상태
     */
    public synchronized EquipmentDesiredState desiredState() {
        return desiredState;
    }

    /**
     * 현재 런타임 상태(runtime state)를 반환합니다.
     *
     * @return 현재 런타임 상태
     */
    public synchronized EquipmentRuntimeState runtimeState() {
        return runtimeState;
    }

    /**
     * 마지막 상태 변경을 발생시킨 이벤트 타입을 반환합니다.
     *
     * @return 마지막 이벤트 타입
     */
    public synchronized String lastEventType() {
        return lastEventType;
    }

    /**
     * 마지막 상태 변경 요청의 traceId를 반환합니다.
     *
     * @return 마지막 traceId
     */
    public synchronized String lastTraceId() {
        return lastTraceId;
    }

    /**
     * 컨텍스트가 마지막으로 갱신된 시각을 반환합니다.
     *
     * @return 마지막 갱신 시각
     */
    public synchronized OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
