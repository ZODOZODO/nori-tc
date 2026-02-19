package com.nori.tc.comm.adapters.kafka.ui;

import com.nori.tc.comm.adapters.kafka.contract.GatewayBusinessCommandMessage;
import com.nori.tc.comm.adapters.kafka.subscribe.GatewayCommandDispatcher;
import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.gateway.comm.ConnectionMode;
import com.nori.tc.comm.gateway.comm.EquipmentChannel;
import com.nori.tc.comm.gateway.comm.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.comm.GatewayConnectionControlPort;
import com.nori.tc.comm.gateway.comm.GatewayProcessingService;
import com.nori.tc.comm.gateway.config.GatewayUiTaskPolicyProperties;
import com.nori.tc.comm.gateway.context.EquipmentContext;
import com.nori.tc.comm.gateway.context.EquipmentContextProfile;
import com.nori.tc.comm.gateway.context.EquipmentContextProfileProvider;
import com.nori.tc.comm.gateway.context.EquipmentContextRegistry;
import com.nori.tc.comm.gateway.context.EquipmentDesiredState;
import com.nori.tc.comm.gateway.context.EquipmentRuntimeState;
import com.nori.tc.comm.gateway.context.EquipmentStatePersistencePort;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.lifecycle.EqpLifecycleStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * UI Task로 들어오는 장비 제어 요청을 통합 처리하는 서비스입니다.
 *
 * <p>주요 책임:</p>
 * <p>1) CREATE/UPDATE/DELETE/START/END/SEND_MESSAGE를 단일 서비스에서 처리합니다.</p>
 * <p>2) 장비 컨텍스트 조회/생성, 상태 전이, 채널 상태 검증을 수행합니다.</p>
 * <p>3) 정책에 따라 동기 대기 방식 또는 비동기 lifecycle 요청 방식으로 동작합니다.</p>
 */
@Service
public class GatewayUiRuntimeControlService {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiRuntimeControlService.class);

    /** 요청 timeout이 비정상(0 이하)일 때 적용할 기본 timeout(ms)입니다. */
    private static final long DEFAULT_RUNTIME_CONTROL_TIMEOUT_MS = 30_000L;

    /** 상태 전이 대기 루프의 초기 poll 간격(ms)입니다. */
    private static final long INITIAL_WAIT_POLL_INTERVAL_MS = 25L;

    /** 상태 전이 대기 루프의 최대 poll 간격(ms)입니다. */
    private static final long MAX_WAIT_POLL_INTERVAL_MS = 500L;

    /** UI 발행자 식별 문자열입니다. */
    private static final String UI_SOURCE = "TC-UI-BACKEND-APP";

    /** SEND_MESSAGE 요청 시 command metadata에 기록할 이벤트 타입입니다. */
    private static final String SEND_MESSAGE_EVENT_TYPE = "EQP_SEND_MESSAGE";

    private final EquipmentContextRegistry contextRegistry;
    private final EquipmentContextProfileProvider profileProvider;
    private final EquipmentStatePersistencePort statePersistencePort;
    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayConnectionControlPort connectionControlPort;
    private final GatewayProcessingService processingService;
    private final GatewayUiTaskPolicyProperties uiTaskPolicyProperties;
    private final EqpLifecycleStateMachine lifecycleStateMachine;
    private final GatewayCommandDispatcher commandDispatcher;

    /**
     * Runtime 제어 통합 서비스 의존성을 초기화합니다.
     *
     * @param contextRegistry 장비 컨텍스트 저장소
     * @param profileProvider 장비 프로필 조회 포트
     * @param statePersistencePortProvider 상태 이력 저장 포트 Provider
     * @param channelRegistry 장비 채널 레지스트리
     * @param connectionControlPort 연결 제어 포트
     * @param processingService 메시지 처리/메일박스 서비스
     * @param uiTaskPolicyProperties UI Task 정책
     * @param lifecycleStateMachine 비동기 lifecycle 상태머신
     * @param commandDispatcher gateway command 디스패처
     */
    public GatewayUiRuntimeControlService(
            final EquipmentContextRegistry contextRegistry,
            final EquipmentContextProfileProvider profileProvider,
            final ObjectProvider<EquipmentStatePersistencePort> statePersistencePortProvider,
            final EquipmentChannelRegistry channelRegistry,
            final GatewayConnectionControlPort connectionControlPort,
            final GatewayProcessingService processingService,
            final GatewayUiTaskPolicyProperties uiTaskPolicyProperties,
            final EqpLifecycleStateMachine lifecycleStateMachine,
            final GatewayCommandDispatcher commandDispatcher
    ) {
        this.contextRegistry = Objects.requireNonNull(contextRegistry, "contextRegistry is null");
        this.profileProvider = Objects.requireNonNull(profileProvider, "profileProvider is null");
        this.statePersistencePort = statePersistencePortProvider.getIfAvailable(() -> EquipmentStatePersistencePort.NO_OP);
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry is null");
        this.connectionControlPort = Objects.requireNonNull(connectionControlPort, "connectionControlPort is null");
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        this.uiTaskPolicyProperties = Objects.requireNonNull(uiTaskPolicyProperties, "uiTaskPolicyProperties is null");
        this.lifecycleStateMachine = Objects.requireNonNull(lifecycleStateMachine, "lifecycleStateMachine is null");
        this.commandDispatcher = Objects.requireNonNull(commandDispatcher, "commandDispatcher is null");
        log.info("Gateway UI runtime control service initialized.");
    }

    /**
     * CREATE/UPDATE 요청을 처리하여 장비 컨텍스트를 생성하거나 갱신합니다.
     *
     * <p>처리 순서:</p>
     * <p>1) eqpId/interfaceType 유효성 검증</p>
     * <p>2) 장비 프로필 조회 및 인터페이스 타입 일치 검증</p>
     * <p>3) 기존 컨텍스트가 있으면 상태를 유지하고, 없으면 기본 상태로 생성</p>
     * <p>4) 상태 이력 저장</p>
     *
     * @param eqpId 장비 ID
     * @param interfaceType 요청 인터페이스 타입
     * @param traceId 요청 traceId
     * @param eventType 원본 UI eventType
     * @param timeoutMs 요청 timeout(ms)
     * @return 검증된 장비 정보
     */
    public GatewayEquipmentInfo createOrUpdateContext(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final String eventType,
            final long timeoutMs
    ) {
        final String normalizedEqpId = requireEqpId(eqpId);
        final CommInterfaceType requestedType = parseInterfaceType(interfaceType);
        final EquipmentContextProfile profile = profileProvider.findProfileById(normalizedEqpId).orElseThrow(
                () -> new ProcessingException(ErrorCode.EQP_NOT_FOUND, "Equipment profile not found")
        );
        final GatewayEquipmentInfo equipmentInfo = profile.equipmentInfo();
        validateInterfaceType(equipmentInfo, requestedType);

        final EquipmentContext existing = contextRegistry.find(normalizedEqpId).orElse(null);
        final EquipmentDesiredState desiredState = existing == null
                ? (equipmentInfo.enabled() ? EquipmentDesiredState.STARTED : EquipmentDesiredState.ENDED)
                : existing.desiredState();
        final EquipmentRuntimeState runtimeState = existing == null
                ? (equipmentInfo.enabled() ? EquipmentRuntimeState.DISCONNECTED : EquipmentRuntimeState.REGISTERED)
                : existing.runtimeState();

        contextRegistry.upsertProfile(profile, desiredState, runtimeState, eventType, traceId);
        statePersistencePort.recordCreateOrUpdate(normalizedEqpId, traceId, eventType, "UI create/update request processed");

        log.info(
                "UI context upsert completed. eventType={}, eqpId={}, traceId={}, enabled={}",
                eventType,
                normalizedEqpId,
                traceId,
                equipmentInfo.enabled()
        );
        if (log.isDebugEnabled()) {
            log.debug(
                    "UI context upsert detail. timeoutMs={}, desiredState={}, runtimeState={}",
                    timeoutMs,
                    desiredState,
                    runtimeState
            );
        }
        return equipmentInfo;
    }

    /**
     * START 요청을 처리합니다.
     *
     * <p>정책 분기:</p>
     * <p>1) lifecycleSyncWaitEnabled=false: 상태머신에 START 요청만 등록하고 즉시 반환</p>
     * <p>2) lifecycleSyncWaitEnabled=true: 연결 완료까지 동기 대기 후 성공/타임아웃 판정</p>
     *
     * @param eqpId 장비 ID
     * @param interfaceType 요청 인터페이스 타입
     * @param traceId 요청 traceId
     * @param timeoutMs 요청 timeout(ms)
     */
    public void startRuntime(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final long timeoutMs
    ) {
        final long boundedTimeoutMs = normalizeTimeoutMs(timeoutMs);
        final String normalizedEqpId = requireEqpId(eqpId);
        final GatewayEquipmentInfo equipmentInfo = resolveAndValidateEquipment(
                normalizedEqpId,
                interfaceType,
                traceId,
                "EQP_START"
        );
        final EquipmentContext context = resolveOrLoadContext(normalizedEqpId, traceId, "EQP_START");

        if (!equipmentInfo.enabled()) {
            throw new ProcessingException(ErrorCode.EQP_DISABLED, "Equipment is disabled");
        }

        if (!uiTaskPolicyProperties.isLifecycleSyncWaitEnabled()) {
            lifecycleStateMachine.requestStart(normalizedEqpId, traceId, boundedTimeoutMs);
            if (equipmentInfo.connectionMode() == ConnectionMode.ACTIVE) {
                connectionControlPort.resumeActiveReconnect(normalizedEqpId);
                connectionControlPort.connectActiveIfPossible(normalizedEqpId);
            }
            log.info(
                    "LIFECYCLE_REQUEST_ACCEPTED. eqpId={}, transition=START, mode={}, traceId={}, timeoutMs={}",
                    normalizedEqpId,
                    equipmentInfo.connectionMode(),
                    traceId,
                    boundedTimeoutMs
            );
            return;
        }

        context.updateDesiredState(EquipmentDesiredState.STARTED, "EQP_START", traceId);
        if (isChannelActive(normalizedEqpId)) {
            context.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "EQP_START", traceId);
            recordStartState(normalizedEqpId, traceId, "UI start request already connected");
            log.info("Runtime start completed immediately (already connected). eqpId={}, traceId={}", normalizedEqpId, traceId);
            return;
        }

        context.updateRuntimeState(EquipmentRuntimeState.CONNECTING, "EQP_START", traceId);
        if (equipmentInfo.connectionMode() == ConnectionMode.ACTIVE) {
            connectionControlPort.resumeActiveReconnect(normalizedEqpId);
            connectionControlPort.connectActiveIfPossible(normalizedEqpId);
            log.info("Active runtime start requested. eqpId={}, traceId={}", normalizedEqpId, traceId);
        } else {
            log.info(
                    "Passive runtime start requested. waiting for inbound connection. eqpId={}, traceId={}",
                    normalizedEqpId,
                    traceId
            );
        }

        waitUntilConnected(normalizedEqpId, traceId, boundedTimeoutMs);
        context.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "EQP_START", traceId);
        recordStartState(normalizedEqpId, traceId, "UI start request processed");
        log.info("Runtime start completed. eqpId={}, traceId={}, timeoutMs={}", normalizedEqpId, traceId, boundedTimeoutMs);
    }

    /**
     * END 요청을 처리합니다.
     *
     * <p>정책 분기:</p>
     * <p>1) lifecycleSyncWaitEnabled=false: 상태머신에 END 요청만 등록하고 즉시 반환</p>
     * <p>2) lifecycleSyncWaitEnabled=true: 채널 종료 후 완전 단절 상태까지 동기 대기</p>
     *
     * @param eqpId 장비 ID
     * @param interfaceType 요청 인터페이스 타입
     * @param traceId 요청 traceId
     * @param timeoutMs 요청 timeout(ms)
     */
    public void endRuntime(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final long timeoutMs
    ) {
        final long boundedTimeoutMs = normalizeTimeoutMs(timeoutMs);
        final String normalizedEqpId = requireEqpId(eqpId);
        final GatewayEquipmentInfo equipmentInfo = resolveAndValidateEquipment(
                normalizedEqpId,
                interfaceType,
                traceId,
                "EQP_END"
        );
        final EquipmentContext context = resolveOrLoadContext(normalizedEqpId, traceId, "EQP_END");

        if (!uiTaskPolicyProperties.isLifecycleSyncWaitEnabled()) {
            connectionControlPort.suppressActiveReconnect(normalizedEqpId);
            final EquipmentChannel channel = channelRegistry.get(new EquipmentId(normalizedEqpId));
            if (channel != null && channel.isActive()) {
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

        context.updateDesiredState(EquipmentDesiredState.ENDED, "EQP_END", traceId);
        connectionControlPort.suppressActiveReconnect(normalizedEqpId);

        final EquipmentChannel channel = channelRegistry.get(new EquipmentId(normalizedEqpId));
        if (channel != null && channel.isActive()) {
            channel.close();
        }

        waitUntilDisconnected(normalizedEqpId, traceId, boundedTimeoutMs);
        context.updateRuntimeState(EquipmentRuntimeState.DISCONNECTED, "EQP_END", traceId);
        processingService.removeMailbox(normalizedEqpId);
        recordEndState(normalizedEqpId, traceId, "UI end request processed");
        log.info("Runtime end completed. eqpId={}, traceId={}, timeoutMs={}", normalizedEqpId, traceId, boundedTimeoutMs);
    }

    /**
     * DELETE 요청을 처리합니다.
     *
     * <p>삭제 조건:</p>
     * <p>1) 컨텍스트가 존재해야 함</p>
     * <p>2) desiredState가 STARTED가 아니어야 함</p>
     * <p>3) 활성 채널이 없어야 함</p>
     *
     * @param eqpId 장비 ID
     * @param interfaceType 요청 인터페이스 타입
     * @param traceId 요청 traceId
     * @param timeoutMs 요청 timeout(ms), 로깅 용도
     */
    public void deleteRuntimeContext(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final long timeoutMs
    ) {
        final String normalizedEqpId = requireEqpId(eqpId);
        final CommInterfaceType requestedType = parseInterfaceType(interfaceType);
        final EquipmentContext context = contextRegistry.find(normalizedEqpId).orElseThrow(
                () -> new ProcessingException(ErrorCode.EQP_CONTEXT_NOT_FOUND, "Equipment context not found")
        );
        validateInterfaceType(context.profile().equipmentInfo(), requestedType);

        final EquipmentChannel channel = channelRegistry.get(new EquipmentId(normalizedEqpId));
        if (context.desiredState() == EquipmentDesiredState.STARTED || (channel != null && channel.isActive())) {
            throw new ProcessingException(ErrorCode.EQP_RUNNING, "Equipment must be ended before delete");
        }

        connectionControlPort.suppressActiveReconnect(normalizedEqpId);
        processingService.removeMailbox(normalizedEqpId);
        contextRegistry.remove(normalizedEqpId, "EQP_DELETE", traceId);
        recordDeleteState(normalizedEqpId, traceId, "UI delete request processed");

        if (log.isDebugEnabled()) {
            log.debug("Runtime delete detail. eqpId={}, traceId={}, timeoutMs={}", normalizedEqpId, traceId, timeoutMs);
        }
        log.info("Runtime context deleted. eqpId={}, traceId={}", normalizedEqpId, traceId);
    }

    /**
     * SEND_MESSAGE 요청을 처리합니다.
     *
     * <p>검증 조건:</p>
     * <p>1) uiMessage 필수</p>
     * <p>2) 장비 enabled=true</p>
     * <p>3) desiredState=STARTED</p>
     * <p>4) 채널 active=true</p>
     *
     * @param eqpId 장비 ID
     * @param interfaceType 요청 인터페이스 타입
     * @param traceId 요청 traceId
     * @param uiMessage 전송할 UI 메시지
     * @param timeoutMs 요청 timeout(ms), 로깅 용도
     */
    public void sendUiMessage(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final String uiMessage,
            final long timeoutMs
    ) {
        if (uiMessage == null || uiMessage.isBlank()) {
            throw new ProcessingException(ErrorCode.UI_MESSAGE_REQUIRED, "uiMessage is required");
        }

        final String normalizedEqpId = requireEqpId(eqpId);
        final EquipmentContext context = resolveOrLoadContext(normalizedEqpId, traceId, "EQP_SEND_MESSAGE");
        final GatewayEquipmentInfo equipmentInfo = resolveAndValidateEquipment(
                normalizedEqpId,
                interfaceType,
                traceId,
                "EQP_SEND_MESSAGE"
        );
        if (!equipmentInfo.enabled()) {
            throw new ProcessingException(ErrorCode.EQP_DISABLED, "Equipment is disabled");
        }
        if (context.desiredState() != EquipmentDesiredState.STARTED) {
            throw new ProcessingException(ErrorCode.EQP_NOT_STARTED, "Equipment is not started");
        }

        final EquipmentChannel channel = channelRegistry.get(new EquipmentId(normalizedEqpId));
        if (channel == null || !channel.isActive()) {
            throw new ProcessingException(ErrorCode.EQP_NOT_CONNECTED, "Equipment channel is not connected");
        }

        final GatewayBusinessCommandMessage commandMessage = new GatewayBusinessCommandMessage(
                new GatewayBusinessCommandMessage.GatewayBusinessCommandMetadata(
                        SEND_MESSAGE_EVENT_TYPE,
                        Instant.now().toString(),
                        UI_SOURCE,
                        traceId
                ),
                new GatewayBusinessCommandMessage.GatewayBusinessCommandData(
                        null,
                        equipmentInfo.equipmentId(),
                        equipmentInfo.commInterfaceType().name(),
                        null,
                        uiMessage
                )
        );

        try {
            commandDispatcher.dispatchBusinessCommand(commandMessage);
        } catch (Exception ex) {
            log.warn(
                    "UI message command dispatch failed. eqpId={}, traceId={}, eventType={}",
                    normalizedEqpId,
                    traceId,
                    SEND_MESSAGE_EVENT_TYPE,
                    ex
            );
            throw new ProcessingException(ErrorCode.INTERNAL_ERROR, "Failed to dispatch send-message command");
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "UI message forwarded to command dispatcher. eqpId={}, traceId={}, timeoutMs={}",
                    normalizedEqpId,
                    traceId,
                    timeoutMs
            );
        }
    }

    /**
     * 장비 정보를 조회하고 요청 interfaceType 일치 여부를 검증합니다.
     *
     * @param eqpId 장비 ID
     * @param interfaceType 요청 인터페이스 타입
     * @param traceId 요청 traceId
     * @param eventType 컨텍스트 생성 시 기록할 eventType
     * @return 검증된 장비 정보
     */
    public GatewayEquipmentInfo resolveAndValidateEquipment(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final String eventType
    ) {
        final CommInterfaceType requestedType = parseInterfaceType(interfaceType);
        final EquipmentContext context = resolveOrLoadContext(eqpId, traceId, eventType);
        final GatewayEquipmentInfo equipmentInfo = context.profile().equipmentInfo();
        validateInterfaceType(equipmentInfo, requestedType);
        return equipmentInfo;
    }

    /**
     * ACTIVE 모드 장비에 대해 즉시 연결 재시도를 트리거합니다.
     *
     * @param equipmentInfo 장비 정보
     */
    public void startActiveIfNeeded(final GatewayEquipmentInfo equipmentInfo) {
        Objects.requireNonNull(equipmentInfo, "equipmentInfo is null");
        if (equipmentInfo.connectionMode() != ConnectionMode.ACTIVE) {
            return;
        }
        connectionControlPort.resumeActiveReconnect(equipmentInfo.equipmentId());
        connectionControlPort.connectActiveIfPossible(equipmentInfo.equipmentId());
        log.info("Active runtime start requested. eqpId={}", equipmentInfo.equipmentId());
    }

    /**
     * 외부 stop 요청을 END 흐름으로 위임합니다.
     *
     * @param eqpId 장비 ID
     */
    public void stopRuntime(final String eqpId) {
        final String interfaceType = resolveInterfaceTypeName(eqpId);
        endRuntime(eqpId, interfaceType, "RUNTIME_STOP", DEFAULT_RUNTIME_CONTROL_TIMEOUT_MS);
    }

    /**
     * 장비 컨텍스트를 조회하고, 없으면 프로필 기반으로 즉시 생성합니다.
     *
     * @param eqpId 장비 ID
     * @param traceId traceId
     * @param eventType 기록용 eventType
     * @return 기존 또는 신규 컨텍스트
     */
    private EquipmentContext resolveOrLoadContext(final String eqpId, final String traceId, final String eventType) {
        final String normalizedEqpId = requireEqpId(eqpId);
        return contextRegistry.find(normalizedEqpId).orElseGet(() -> {
            final EquipmentContextProfile profile = profileProvider.findProfileById(normalizedEqpId).orElseThrow(
                    () -> new ProcessingException(ErrorCode.EQP_NOT_FOUND, "Equipment profile not found")
            );
            final GatewayEquipmentInfo info = profile.equipmentInfo();
            final EquipmentDesiredState desiredState = info.enabled() ? EquipmentDesiredState.STARTED : EquipmentDesiredState.ENDED;
            final EquipmentRuntimeState runtimeState = info.enabled() ? EquipmentRuntimeState.DISCONNECTED : EquipmentRuntimeState.REGISTERED;
            if (log.isDebugEnabled()) {
                log.debug(
                        "Equipment context loaded lazily. eqpId={}, eventType={}, desiredState={}, runtimeState={}",
                        normalizedEqpId,
                        eventType,
                        desiredState,
                        runtimeState
                );
            }
            return contextRegistry.upsertProfile(profile, desiredState, runtimeState, eventType, traceId);
        });
    }

    /**
     * 현재 장비 채널이 활성 상태인지 확인합니다.
     *
     * @param eqpId 장비 ID
     * @return 활성 상태면 true
     */
    private boolean isChannelActive(final String eqpId) {
        final EquipmentChannel channel = channelRegistry.get(new EquipmentId(eqpId));
        return channel != null && channel.isActive();
    }

    /**
     * 연결 완료 상태가 될 때까지 대기합니다.
     *
     * @param eqpId 장비 ID
     * @param traceId traceId
     * @param timeoutMs timeout(ms)
     */
    private void waitUntilConnected(final String eqpId, final String traceId, final long timeoutMs) {
        waitUntilCondition(eqpId, timeoutMs, () -> isChannelActive(eqpId));
        if (isChannelActive(eqpId)) {
            return;
        }
        contextRegistry.find(eqpId).ifPresent(context ->
                context.updateRuntimeState(EquipmentRuntimeState.DISCONNECTED, "EQP_START_TIMEOUT", traceId)
        );
        log.warn("Runtime start timeout. eqpId={}, traceId={}, timeoutMs={}", eqpId, traceId, timeoutMs);
        throw new ProcessingException(ErrorCode.EQP_START_TIMEOUT, "Timed out while waiting for equipment connection");
    }

    /**
     * 연결 종료 상태가 될 때까지 대기합니다.
     *
     * @param eqpId 장비 ID
     * @param traceId traceId
     * @param timeoutMs timeout(ms)
     */
    private void waitUntilDisconnected(final String eqpId, final String traceId, final long timeoutMs) {
        waitUntilCondition(eqpId, timeoutMs, () -> !isChannelActive(eqpId));
        if (!isChannelActive(eqpId)) {
            return;
        }
        contextRegistry.find(eqpId).ifPresent(context ->
                context.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "EQP_END_TIMEOUT", traceId)
        );
        log.warn("Runtime end timeout. eqpId={}, traceId={}, timeoutMs={}", eqpId, traceId, timeoutMs);
        throw new ProcessingException(ErrorCode.EQP_END_TIMEOUT, "Timed out while waiting for equipment disconnection");
    }

    /**
     * 조건 충족 시까지 지수 백오프로 대기합니다.
     *
     * @param eqpId 장비 ID
     * @param timeoutMs 최대 대기시간(ms)
     * @param completionCondition 완료 조건
     */
    private void waitUntilCondition(final String eqpId, final long timeoutMs, final BooleanSupplier completionCondition) {
        final long startMs = System.currentTimeMillis();
        final long deadline = startMs + timeoutMs;
        long pollIntervalMs = INITIAL_WAIT_POLL_INTERVAL_MS;

        while (System.currentTimeMillis() <= deadline) {
            if (completionCondition.getAsBoolean()) {
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
            log.debug("Wait condition timeout. eqpId={}, timeoutMs={}, elapsedMs={}", eqpId, timeoutMs, elapsedMs);
        }
    }

    /**
     * timeout 값을 보정합니다.
     *
     * @param timeoutMs 요청 timeout(ms)
     * @return 보정된 timeout(ms)
     */
    private long normalizeTimeoutMs(final long timeoutMs) {
        return timeoutMs <= 0L ? DEFAULT_RUNTIME_CONTROL_TIMEOUT_MS : timeoutMs;
    }

    /**
     * 주어진 시간만큼 안전하게 park 대기합니다.
     *
     * @param waitMs 대기 시간(ms)
     * @param eqpId 장비 ID
     */
    private void parkQuietly(final long waitMs, final String eqpId) {
        if (waitMs <= 0L) {
            return;
        }
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(waitMs));
        if (Thread.currentThread().isInterrupted()) {
            throw new ProcessingException(
                    ErrorCode.INTERNAL_ERROR,
                    "Interrupted while waiting for runtime state transition. eqpId=" + eqpId
            );
        }
    }

    /**
     * interfaceType 문자열을 enum으로 변환합니다.
     *
     * @param interfaceType 문자열 interfaceType
     * @return 변환된 enum 값
     */
    private CommInterfaceType parseInterfaceType(final String interfaceType) {
        try {
            return CommInterfaceType.fromText(interfaceType);
        } catch (Exception ex) {
            throw new ProcessingException(ErrorCode.INVALID_INTERFACE_TYPE, "interfaceType is invalid");
        }
    }

    /**
     * eqpId 값을 검증하고 trim 처리합니다.
     *
     * @param eqpId 원본 eqpId
     * @return 정규화된 eqpId
     */
    private String requireEqpId(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new ProcessingException(ErrorCode.EQP_ID_REQUIRED, "eqpId is required");
        }
        return eqpId.trim();
    }

    /**
     * 요청 인터페이스 타입과 장비 프로필 타입의 일치 여부를 검증합니다.
     *
     * @param equipmentInfo 장비 정보
     * @param requestedType 요청 인터페이스 타입
     */
    private void validateInterfaceType(final GatewayEquipmentInfo equipmentInfo, final CommInterfaceType requestedType) {
        if (equipmentInfo.commInterfaceType() != requestedType) {
            throw new ProcessingException(
                    ErrorCode.INTERFACE_MISMATCH,
                    "Requested interfaceType does not match equipment profile"
            );
        }
    }

    /**
     * START 완료 이력을 저장합니다.
     */
    private void recordStartState(final String eqpId, final String traceId, final String detailMessage) {
        statePersistencePort.recordStart(eqpId, traceId, detailMessage);
    }

    /**
     * END 완료 이력을 저장합니다.
     */
    private void recordEndState(final String eqpId, final String traceId, final String detailMessage) {
        statePersistencePort.recordEnd(eqpId, traceId, detailMessage);
    }

    /**
     * DELETE 완료 이력을 저장합니다.
     */
    private void recordDeleteState(final String eqpId, final String traceId, final String detailMessage) {
        statePersistencePort.recordDelete(eqpId, traceId, detailMessage);
    }

    /**
     * 장비 컨텍스트에서 interfaceType 이름을 조회합니다.
     *
     * @param eqpId 장비 ID
     * @return interfaceType 이름
     */
    private String resolveInterfaceTypeName(final String eqpId) {
        final EquipmentContext context = resolveOrLoadContext(
                eqpId,
                "UI_INTERFACE_RESOLVE",
                "UI_INTERFACE_RESOLVE"
        );
        return context.profile().equipmentInfo().commInterfaceType().name();
    }

    /**
     * UI Task 처리 오류 코드를 정의합니다.
     */
    public static final class ErrorCode {
        private ErrorCode() {
        }

        public static final String INVALID_EVENT_TYPE = "INVALID_EVENT_TYPE";
        public static final String INVALID_INTERFACE_TYPE = "INVALID_INTERFACE_TYPE";
        public static final String EQP_ID_REQUIRED = "EQP_ID_REQUIRED";
        public static final String UI_MESSAGE_REQUIRED = "UI_MESSAGE_REQUIRED";
        public static final String HANDLER_NOT_FOUND = "HANDLER_NOT_FOUND";
        public static final String DUPLICATE_TRACE_ID = "DUPLICATE_TRACE_ID";
        public static final String EQP_NOT_FOUND = "EQP_NOT_FOUND";
        public static final String EQP_CONTEXT_NOT_FOUND = "EQP_CONTEXT_NOT_FOUND";
        public static final String INTERFACE_MISMATCH = "INTERFACE_MISMATCH";
        public static final String EQP_DISABLED = "EQP_DISABLED";
        public static final String EQP_NOT_STARTED = "EQP_NOT_STARTED";
        public static final String EQP_NOT_CONNECTED = "EQP_NOT_CONNECTED";
        public static final String EQP_RUNNING = "EQP_RUNNING";
        public static final String EQP_START_TIMEOUT = "EQP_START_TIMEOUT";
        public static final String EQP_END_TIMEOUT = "EQP_END_TIMEOUT";
        public static final String TASK_TIMEOUT = "TASK_TIMEOUT";
        public static final String REPLY_PUBLISH_FAILED = "REPLY_PUBLISH_FAILED";
        public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
        public static final String JARFILE_TASK_NOT_CONFIGURED = "JARFILE_TASK_NOT_CONFIGURED";
        public static final String JARFILE_TASK_FAILED = "JARFILE_TASK_FAILED";
    }

    /**
     * UI Task 처리 중 발생한 비즈니스 예외를 표현합니다.
     */
    public static class ProcessingException extends RuntimeException {
        private final String errorCode;

        /**
         * 비즈니스 예외를 생성합니다.
         *
         * @param errorCode 오류 코드
         * @param message 오류 메시지
         */
        public ProcessingException(final String errorCode, final String message) {
            super(message);
            if (errorCode == null || errorCode.isBlank()) {
                throw new IllegalArgumentException("errorCode is required");
            }
            this.errorCode = errorCode;
        }

        /**
         * 오류 코드를 반환합니다.
         *
         * @return 오류 코드
         */
        public String errorCode() {
            return errorCode;
        }
    }
}
