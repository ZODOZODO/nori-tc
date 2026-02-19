package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.gateway.comm.ConnectionMode;
import com.nori.tc.comm.gateway.comm.EquipmentChannel;
import com.nori.tc.comm.gateway.comm.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.comm.GatewayConnectionControlPort;
import com.nori.tc.comm.gateway.comm.GatewayProcessingService;
import com.nori.tc.comm.gateway.config.GatewayUiTaskPolicyProperties;
import com.nori.tc.comm.gateway.context.EquipmentContext;
import com.nori.tc.comm.gateway.context.EquipmentContextRegistry;
import com.nori.tc.comm.gateway.context.EquipmentDesiredState;
import com.nori.tc.comm.gateway.context.EquipmentRuntimeState;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.lifecycle.EqpLifecycleStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * UI 수명주기 명령(START/END/DELETE)을 처리하는 서비스입니다.
 *
 * <p>동작 모드:</p>
 * <p>1) {@code lifecycleSyncWaitEnabled=false}: 상태머신 비동기 전이 요청을 등록하고 즉시 수락 처리</p>
 * <p>2) {@code lifecycleSyncWaitEnabled=true}: 레거시 동기 대기 경로로 실제 연결/해제 완료까지 확인</p>
 *
 * <p>비동기 모드는 목표 아키텍처(Phase 2) 경로이며, 동기 모드는 점진 전환을 위한 fallback입니다.</p>
 */
@Service
public class GatewayUiLifecycleCommandService {

    /**
     * 처리 흐름 관찰용 로거입니다.
     */
    private static final Logger log = LoggerFactory.getLogger(GatewayUiLifecycleCommandService.class);

    /**
     * 대기 루프 초기 폴링 간격(ms)입니다.
     */
    private static final long INITIAL_WAIT_POLL_INTERVAL_MS = 25L;

    /**
     * 대기 루프 최대 폴링 간격(ms)입니다.
     */
    private static final long MAX_WAIT_POLL_INTERVAL_MS = 500L;

    /**
     * UI 요청에서 timeout이 없거나 비정상 값일 때 적용할 기본 timeout(ms)입니다.
     */
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    /**
     * UI 요청 공통 검증/컨텍스트 제어 서비스입니다.
     */
    private final GatewayUiContextCommandService contextCommandService;

    /**
     * 설비 런타임 컨텍스트 저장소입니다.
     */
    private final EquipmentContextRegistry contextRegistry;

    /**
     * 설비 채널(연결) 조회 레지스트리입니다.
     */
    private final EquipmentChannelRegistry channelRegistry;

    /**
     * ACTIVE 재접속 제어 포트입니다.
     */
    private final GatewayConnectionControlPort connectionControlPort;

    /**
     * 메일박스 관리 등 게이트웨이 처리 서비스입니다.
     */
    private final GatewayProcessingService processingService;

    /**
     * UI task 정책 프로퍼티입니다.
     */
    private final GatewayUiTaskPolicyProperties uiTaskPolicyProperties;

    /**
     * 설비 수명주기 상태머신입니다(비동기 전이 경로).
     */
    private final EqpLifecycleStateMachine lifecycleStateMachine;

    /**
     * 수명주기 명령 처리에 필요한 의존성을 주입합니다.
     *
     * @param contextCommandService UI 명령 공통 검증/컨텍스트 제어 서비스
     * @param contextRegistry 설비 컨텍스트 레지스트리
     * @param channelRegistry 설비 채널 레지스트리
     * @param connectionControlPort ACTIVE 접속 제어 포트
     * @param processingService 게이트웨이 처리 서비스
     * @param uiTaskPolicyProperties UI task 정책
     * @param lifecycleStateMachine 수명주기 상태머신
     */
    public GatewayUiLifecycleCommandService(
            final GatewayUiContextCommandService contextCommandService,
            final EquipmentContextRegistry contextRegistry,
            final EquipmentChannelRegistry channelRegistry,
            final GatewayConnectionControlPort connectionControlPort,
            final GatewayProcessingService processingService,
            final GatewayUiTaskPolicyProperties uiTaskPolicyProperties,
            final EqpLifecycleStateMachine lifecycleStateMachine
    ) {
        this.contextCommandService = Objects.requireNonNull(contextCommandService, "contextCommandService is null");
        this.contextRegistry = Objects.requireNonNull(contextRegistry, "contextRegistry is null");
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry is null");
        this.connectionControlPort = Objects.requireNonNull(connectionControlPort, "connectionControlPort is null");
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        this.uiTaskPolicyProperties = Objects.requireNonNull(uiTaskPolicyProperties, "uiTaskPolicyProperties is null");
        this.lifecycleStateMachine = Objects.requireNonNull(lifecycleStateMachine, "lifecycleStateMachine is null");
    }

    /**
     * START 명령을 처리합니다.
     *
     * <p>syncWait=false이면 상태머신에 START 전이를 요청하고 즉시 반환합니다.
     * syncWait=true이면 실제 연결 완료까지 대기한 뒤 완료 상태를 기록합니다.</p>
     *
     * @param eqpId 설비 ID
     * @param interfaceType 요청 인터페이스 타입
     * @param traceId 추적 ID
     * @param timeoutMs UI 요청 timeout(ms), 0 이하이면 기본값으로 보정
     */
    public void startRuntime(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final long timeoutMs
    ) {
        final long boundedTimeoutMs = normalizeTimeoutMs(timeoutMs);
        final String normalizedEqpId = contextCommandService.requireEqpId(eqpId);
        final GatewayEquipmentInfo equipmentInfo = contextCommandService.resolveAndValidateEquipment(
                normalizedEqpId,
                interfaceType,
                traceId,
                "EQP_START"
        );
        final EquipmentContext context = contextCommandService.resolveOrLoadContext(
                normalizedEqpId,
                traceId,
                "EQP_START"
        );

        if (log.isDebugEnabled()) {
            log.debug(
                    "Runtime start requested. eqpId={}, traceId={}, interfaceType={}, requestedTimeoutMs={}, boundedTimeoutMs={}",
                    normalizedEqpId,
                    traceId,
                    interfaceType,
                    timeoutMs,
                    boundedTimeoutMs
            );
        }

        if (!equipmentInfo.enabled()) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.EQP_DISABLED,
                    "Equipment is disabled"
            );
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "Runtime start lifecycle policy resolved. eqpId={}, syncWaitEnabled={}",
                    normalizedEqpId,
                    uiTaskPolicyProperties.isLifecycleSyncWaitEnabled()
            );
        }

        // Phase 2 기본 경로: 상태머신에 비동기 전이를 요청하고 즉시 ACCEPT 응답을 반환합니다.
        if (!uiTaskPolicyProperties.isLifecycleSyncWaitEnabled()) {
            lifecycleStateMachine.requestStart(normalizedEqpId, traceId, boundedTimeoutMs);

            if (equipmentInfo.connectionMode() == ConnectionMode.ACTIVE) {
                connectionControlPort.resumeActiveReconnect(normalizedEqpId);
                connectionControlPort.connectActiveIfPossible(normalizedEqpId);
                log.info(
                        "LIFECYCLE_REQUEST_ACCEPTED. eqpId={}, transition=START, mode=ACTIVE, traceId={}, timeoutMs={}",
                        normalizedEqpId,
                        traceId,
                        boundedTimeoutMs
                );
            } else {
                log.info(
                        "LIFECYCLE_REQUEST_ACCEPTED. eqpId={}, transition=START, mode=PASSIVE, traceId={}, timeoutMs={}",
                        normalizedEqpId,
                        traceId,
                        boundedTimeoutMs
                );
            }
            return;
        }

        // 레거시 fallback: desired/runtime 상태를 즉시 갱신하고 실제 연결 완료까지 동기 대기합니다.
        context.updateDesiredState(EquipmentDesiredState.STARTED, "EQP_START", traceId);

        if (isChannelActive(normalizedEqpId)) {
            context.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "EQP_START", traceId);
            contextCommandService.recordStartState(normalizedEqpId, traceId, "UI start request already connected");
            log.info(
                    "Runtime start completed immediately (already connected). eqpId={}, traceId={}",
                    normalizedEqpId,
                    traceId
            );
            return;
        }

        context.updateRuntimeState(EquipmentRuntimeState.CONNECTING, "EQP_START", traceId);

        if (equipmentInfo.connectionMode() == ConnectionMode.ACTIVE) {
            log.info("Active runtime start requested. eqpId={}, traceId={}", normalizedEqpId, traceId);
            connectionControlPort.resumeActiveReconnect(normalizedEqpId);
            connectionControlPort.connectActiveIfPossible(normalizedEqpId);
        } else {
            log.info(
                    "Passive runtime start requested. waiting for inbound connection. eqpId={}, traceId={}",
                    normalizedEqpId,
                    traceId
            );
        }

        waitUntilConnected(normalizedEqpId, traceId, boundedTimeoutMs);
        context.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "EQP_START", traceId);
        contextCommandService.recordStartState(normalizedEqpId, traceId, "UI start request processed");

        log.info(
                "Runtime start completed. eqpId={}, traceId={}, timeoutMs={}",
                normalizedEqpId,
                traceId,
                boundedTimeoutMs
        );
    }

    /**
     * END 명령을 처리합니다.
     *
     * <p>syncWait=false이면 상태머신에 END 전이를 요청하고 즉시 반환합니다.
     * syncWait=true이면 채널 종료 및 연결 해제 완료까지 대기한 뒤 종료 상태를 기록합니다.</p>
     *
     * @param eqpId 설비 ID
     * @param interfaceType 요청 인터페이스 타입
     * @param traceId 추적 ID
     * @param timeoutMs UI 요청 timeout(ms), 0 이하이면 기본값으로 보정
     */
    public void endRuntime(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final long timeoutMs
    ) {
        final long boundedTimeoutMs = normalizeTimeoutMs(timeoutMs);
        final String normalizedEqpId = contextCommandService.requireEqpId(eqpId);
        final GatewayEquipmentInfo equipmentInfo = contextCommandService.resolveAndValidateEquipment(
                normalizedEqpId,
                interfaceType,
                traceId,
                "EQP_END"
        );
        final EquipmentContext context = contextCommandService.resolveOrLoadContext(
                normalizedEqpId,
                traceId,
                "EQP_END"
        );

        if (log.isDebugEnabled()) {
            log.debug(
                    "Runtime end requested. eqpId={}, traceId={}, interfaceType={}, requestedTimeoutMs={}, boundedTimeoutMs={}",
                    normalizedEqpId,
                    traceId,
                    interfaceType,
                    timeoutMs,
                    boundedTimeoutMs
            );
            log.debug(
                    "Runtime end lifecycle policy resolved. eqpId={}, syncWaitEnabled={}",
                    normalizedEqpId,
                    uiTaskPolicyProperties.isLifecycleSyncWaitEnabled()
            );
        }

        // Phase 2 기본 경로: reconnect 억제 후 상태머신 비동기 END 전이를 등록합니다.
        if (!uiTaskPolicyProperties.isLifecycleSyncWaitEnabled()) {
            connectionControlPort.suppressActiveReconnect(normalizedEqpId);

            final EquipmentChannel channel = channelRegistry.get(new EquipmentId(normalizedEqpId));
            if (channel != null && channel.isActive()) {
                if (log.isDebugEnabled()) {
                    log.debug(
                            "Closing active channel by async runtime end. eqpId={}, traceId={}",
                            normalizedEqpId,
                            traceId
                    );
                }
                channel.close();
            }

            lifecycleStateMachine.requestEnd(normalizedEqpId, traceId, boundedTimeoutMs);
            log.info(
                    "LIFECYCLE_REQUEST_ACCEPTED. eqpId={}, transition=END, mode={}, traceId={}, timeoutMs={}",
                    normalizedEqpId,
                    equipmentInfo.connectionMode(),
                    traceId,
                    boundedTimeoutMs
            );
            return;
        }

        // 레거시 fallback: desired/runtime 상태를 갱신하고 실제 연결 해제까지 동기 대기합니다.
        context.updateDesiredState(EquipmentDesiredState.ENDED, "EQP_END", traceId);
        log.info(
                "Runtime end requested. eqpId={}, traceId={}, mode={}",
                normalizedEqpId,
                traceId,
                equipmentInfo.connectionMode()
        );

        connectionControlPort.suppressActiveReconnect(normalizedEqpId);

        final EquipmentChannel channel = channelRegistry.get(new EquipmentId(normalizedEqpId));
        if (channel != null && channel.isActive()) {
            if (log.isDebugEnabled()) {
                log.debug("Closing active channel by runtime end. eqpId={}, traceId={}", normalizedEqpId, traceId);
            }
            channel.close();
        }

        waitUntilDisconnected(normalizedEqpId, traceId, boundedTimeoutMs);
        context.updateRuntimeState(EquipmentRuntimeState.DISCONNECTED, "EQP_END", traceId);

        processingService.removeMailbox(normalizedEqpId);
        contextCommandService.recordEndState(normalizedEqpId, traceId, "UI end request processed");

        log.info(
                "Runtime end completed. eqpId={}, traceId={}, timeoutMs={}",
                normalizedEqpId,
                traceId,
                boundedTimeoutMs
        );
    }

    /**
     * DELETE 명령을 처리해 설비 런타임 컨텍스트를 제거합니다.
     *
     * <p>START 상태거나 활성 채널이 남아 있으면 삭제를 거부합니다.</p>
     *
     * @param eqpId 설비 ID
     * @param interfaceType 요청 인터페이스 타입
     * @param traceId 추적 ID
     */
    public void deleteRuntimeContext(
            final String eqpId,
            final String interfaceType,
            final String traceId
    ) {
        final String normalizedEqpId = contextCommandService.requireEqpId(eqpId);
        final CommInterfaceType requestedType = contextCommandService.parseInterfaceType(interfaceType);

        if (log.isDebugEnabled()) {
            log.debug(
                    "Runtime delete requested. eqpId={}, traceId={}, interfaceType={}",
                    normalizedEqpId,
                    traceId,
                    interfaceType
            );
        }

        final EquipmentContext context = contextRegistry.find(normalizedEqpId).orElseThrow(
                () -> new GatewayUiTaskProcessingException(
                        GatewayUiTaskErrorCode.EQP_CONTEXT_NOT_FOUND,
                        "Equipment context not found"
                )
        );
        contextCommandService.validateInterfaceType(context.profile().equipmentInfo(), requestedType);

        final EquipmentChannel channel = channelRegistry.get(new EquipmentId(normalizedEqpId));
        if (context.desiredState() == EquipmentDesiredState.STARTED || (channel != null && channel.isActive())) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.EQP_RUNNING,
                    "Equipment must be ended before delete"
            );
        }

        connectionControlPort.suppressActiveReconnect(normalizedEqpId);
        processingService.removeMailbox(normalizedEqpId);
        contextRegistry.remove(normalizedEqpId, "EQP_DELETE", traceId);

        contextCommandService.recordDeleteState(normalizedEqpId, traceId, "UI delete request processed");
        log.info("Runtime context deleted. eqpId={}, traceId={}", normalizedEqpId, traceId);
    }

    /**
     * 레거시 경로에서 ACTIVE 모드 설비의 접속 시작을 보조합니다.
     *
     * @param equipmentInfo 설비 정보
     */
    public void startActiveIfNeeded(final GatewayEquipmentInfo equipmentInfo) {
        Objects.requireNonNull(equipmentInfo, "equipmentInfo is null");
        if (equipmentInfo.connectionMode() != ConnectionMode.ACTIVE) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Active start skipped (not ACTIVE mode). eqpId={}, mode={}",
                        equipmentInfo.equipmentId(),
                        equipmentInfo.connectionMode()
                );
            }
            return;
        }

        log.info("Active runtime start requested. eqpId={}", equipmentInfo.equipmentId());
        connectionControlPort.resumeActiveReconnect(equipmentInfo.equipmentId());
        connectionControlPort.connectActiveIfPossible(equipmentInfo.equipmentId());
    }

    /**
     * 현재 설비 채널이 활성 상태인지 확인합니다.
     *
     * @param eqpId 설비 ID
     * @return 활성 연결이면 true
     */
    private boolean isChannelActive(final String eqpId) {
        final EquipmentChannel channel = channelRegistry.get(new EquipmentId(eqpId));
        return channel != null && channel.isActive();
    }

    /**
     * timeout 내에 설비 연결 완료를 대기합니다.
     *
     * @param eqpId 설비 ID
     * @param traceId 추적 ID
     * @param timeoutMs 대기 timeout(ms)
     */
    private void waitUntilConnected(
            final String eqpId,
            final String traceId,
            final long timeoutMs
    ) {
        waitUntilCondition(eqpId, timeoutMs, () -> isChannelActive(eqpId));
        if (isChannelActive(eqpId)) {
            return;
        }

        contextRegistry.find(eqpId).ifPresent(context ->
                context.updateRuntimeState(EquipmentRuntimeState.DISCONNECTED, "EQP_START_TIMEOUT", traceId)
        );
        log.warn(
                "Runtime start timeout. eqpId={}, traceId={}, timeoutMs={}",
                eqpId,
                traceId,
                timeoutMs
        );
        throw new GatewayUiTaskProcessingException(
                GatewayUiTaskErrorCode.EQP_START_TIMEOUT,
                "Timed out while waiting for equipment connection"
        );
    }

    /**
     * timeout 내에 설비 연결 해제 완료를 대기합니다.
     *
     * @param eqpId 설비 ID
     * @param traceId 추적 ID
     * @param timeoutMs 대기 timeout(ms)
     */
    private void waitUntilDisconnected(
            final String eqpId,
            final String traceId,
            final long timeoutMs
    ) {
        waitUntilCondition(eqpId, timeoutMs, () -> !isChannelActive(eqpId));
        if (!isChannelActive(eqpId)) {
            return;
        }

        contextRegistry.find(eqpId).ifPresent(context ->
                context.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "EQP_END_TIMEOUT", traceId)
        );
        log.warn(
                "Runtime end timeout. eqpId={}, traceId={}, timeoutMs={}",
                eqpId,
                traceId,
                timeoutMs
        );
        throw new GatewayUiTaskProcessingException(
                GatewayUiTaskErrorCode.EQP_END_TIMEOUT,
                "Timed out while waiting for equipment disconnection"
        );
    }

    /**
     * 주어진 완료 조건이 충족되거나 timeout에 도달할 때까지 대기합니다.
     *
     * <p>초기에는 짧은 폴링 간격으로 빠르게 반응하고,
     * 점진적으로 간격을 늘려 CPU 점유율을 낮춥니다.</p>
     *
     * @param eqpId 설비 ID
     * @param timeoutMs 대기 timeout(ms)
     * @param completionCondition 완료 조건
     */
    private void waitUntilCondition(
            final String eqpId,
            final long timeoutMs,
            final BooleanSupplier completionCondition
    ) {
        final long startMs = System.currentTimeMillis();
        final long deadline = startMs + timeoutMs;
        long pollIntervalMs = INITIAL_WAIT_POLL_INTERVAL_MS;

        if (log.isDebugEnabled()) {
            log.debug("Wait condition started. eqpId={}, timeoutMs={}", eqpId, timeoutMs);
        }

        while (System.currentTimeMillis() <= deadline) {
            if (completionCondition.getAsBoolean()) {
                if (log.isDebugEnabled()) {
                    final long elapsedMs = Math.max(0L, System.currentTimeMillis() - startMs);
                    log.debug("Wait condition satisfied. eqpId={}, elapsedMs={}", eqpId, elapsedMs);
                }
                return;
            }

            final long remainingMs = deadline - System.currentTimeMillis();
            if (remainingMs <= 0L) {
                break;
            }

            final long waitMs = Math.min(pollIntervalMs, remainingMs);
            parkQuietly(waitMs, eqpId);
            pollIntervalMs = Math.min(MAX_WAIT_POLL_INTERVAL_MS, pollIntervalMs * 2L);
        }

        if (log.isDebugEnabled()) {
            final long elapsedMs = Math.max(0L, System.currentTimeMillis() - startMs);
            log.debug("Wait condition ended by timeout. eqpId={}, timeoutMs={}, elapsedMs={}", eqpId, timeoutMs, elapsedMs);
        }
    }

    /**
     * timeout 값을 유효 범위로 정규화합니다.
     *
     * @param timeoutMs 요청 timeout(ms)
     * @return 0 이하이면 기본 timeout, 그 외에는 입력값
     */
    private long normalizeTimeoutMs(final long timeoutMs) {
        if (timeoutMs <= 0L) {
            if (log.isDebugEnabled()) {
                log.debug("Invalid timeout detected. fallback to default timeout. requestedTimeoutMs={}, defaultTimeoutMs={}",
                        timeoutMs,
                        DEFAULT_TIMEOUT_MS);
            }
            return DEFAULT_TIMEOUT_MS;
        }
        return timeoutMs;
    }

    /**
     * 인터럽트 상태를 보존하면서 지정 시간만큼 대기합니다.
     *
     * @param waitMs 대기 시간(ms)
     * @param eqpId 설비 ID
     */
    private void parkQuietly(final long waitMs, final String eqpId) {
        if (waitMs <= 0L) {
            return;
        }
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(waitMs));
        if (Thread.currentThread().isInterrupted()) {
            log.warn("Thread interrupted while waiting for runtime state transition. eqpId={}, waitMs={}", eqpId, waitMs);
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.INTERNAL_ERROR,
                    "Interrupted while waiting for runtime state transition. eqpId=" + eqpId
            );
        }
    }
}
