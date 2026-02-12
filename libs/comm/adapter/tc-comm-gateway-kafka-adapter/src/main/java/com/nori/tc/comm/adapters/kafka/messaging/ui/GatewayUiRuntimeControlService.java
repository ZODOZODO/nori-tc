package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.gateway.comm.ConnectionMode;
import com.nori.tc.comm.gateway.comm.EquipmentChannel;
import com.nori.tc.comm.gateway.comm.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.comm.GatewayConnectionControlPort;
import com.nori.tc.comm.gateway.comm.GatewayProcessingService;
import com.nori.tc.comm.gateway.context.EquipmentContext;
import com.nori.tc.comm.gateway.context.EquipmentContextProfile;
import com.nori.tc.comm.gateway.context.EquipmentContextProfileProvider;
import com.nori.tc.comm.gateway.context.EquipmentContextRegistry;
import com.nori.tc.comm.gateway.context.EquipmentDesiredState;
import com.nori.tc.comm.gateway.context.EquipmentRuntimeState;
import com.nori.tc.comm.gateway.context.EquipmentStatePersistencePort;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaCommandDispatcher;
import com.nori.tc.messaging.kafka.starter.contract.KafkaCommandMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * UI task 핸들러에서 공통으로 사용하는 런타임 제어 서비스입니다.
 *
 * <p>주요 책임:</p>
 * <p>- EquipmentContext 조회/갱신</p>
 * <p>- START/END/DELETE 상태 전이</p>
 * <p>- UI SEND_MESSAGE 검증 및 디스패치</p>
 * <p>- tc_eqp_state / tc_eqp_state_hist 반영</p>
 */
@Service
public class GatewayUiRuntimeControlService {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiRuntimeControlService.class);
    private static final long WAIT_POLL_INTERVAL_MS = 100L;
    private static final long DEFAULT_LEGACY_TIMEOUT_MS = 30_000L;

    private final GatewayProcessingService processingService;
    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayConnectionControlPort connectionControlPort;
    private final KafkaCommandDispatcher commandDispatcher;
    private final EquipmentContextRegistry contextRegistry;
    private final EquipmentContextProfileProvider profileProvider;
    private final EquipmentStatePersistencePort statePersistencePort;

    /**
     * 런타임 제어에 필요한 의존성을 초기화합니다.
     */
    public GatewayUiRuntimeControlService(
            final GatewayProcessingService processingService,
            final EquipmentChannelRegistry channelRegistry,
            final GatewayConnectionControlPort connectionControlPort,
            final KafkaCommandDispatcher commandDispatcher,
            final EquipmentContextRegistry contextRegistry,
            final EquipmentContextProfileProvider profileProvider,
            final ObjectProvider<EquipmentStatePersistencePort> statePersistencePortProvider
    ) {
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry is null");
        this.connectionControlPort = Objects.requireNonNull(connectionControlPort, "connectionControlPort is null");
        this.commandDispatcher = Objects.requireNonNull(commandDispatcher, "commandDispatcher is null");
        this.contextRegistry = Objects.requireNonNull(contextRegistry, "contextRegistry is null");
        this.profileProvider = Objects.requireNonNull(profileProvider, "profileProvider is null");
        this.statePersistencePort = statePersistencePortProvider.getIfAvailable(() -> EquipmentStatePersistencePort.NO_OP);
    }

    /**
     * CREATE/UPDATE 요청을 처리합니다.
     *
     * <p>timeoutMs는 정책 일관성을 위해 입력받으며, 현재는 DB 조회 + 컨텍스트 갱신 처리에 사용됩니다.</p>
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
                () -> new GatewayUiTaskProcessingException(
                        GatewayUiTaskErrorCode.EQP_NOT_FOUND,
                        "Equipment profile not found"
                )
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
        statePersistencePort.recordCreateOrUpdate(
                normalizedEqpId,
                traceId,
                eventType,
                "UI create/update request processed"
        );

        log.info("Context upsert completed. eventType={}, eqpId={}, traceId={}, enabled={}, timeoutMs={}",
                eventType,
                normalizedEqpId,
                traceId,
                equipmentInfo.enabled(),
                timeoutMs);
        return equipmentInfo;
    }

    /**
     * START 요청을 처리합니다.
     *
     * <p>PASS 조건: timeout 내 실제 채널 연결(active=true) 성공</p>
     */
    public void startRuntime(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final long timeoutMs
    ) {
        final long boundedTimeoutMs = normalizeTimeoutMs(timeoutMs);
        final String normalizedEqpId = requireEqpId(eqpId);
        final CommInterfaceType requestedType = parseInterfaceType(interfaceType);

        final EquipmentContext context = resolveOrLoadContext(normalizedEqpId, traceId, "EQP_START");
        final GatewayEquipmentInfo equipmentInfo = context.profile().equipmentInfo();

        validateInterfaceType(equipmentInfo, requestedType);
        if (!equipmentInfo.enabled()) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.EQP_DISABLED,
                    "Equipment is disabled"
            );
        }

        context.updateDesiredState(EquipmentDesiredState.STARTED, "EQP_START", traceId);

        // 이미 연결된 상태라면 즉시 PASS 처리합니다.
        if (isChannelActive(normalizedEqpId)) {
            context.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "EQP_START", traceId);
            statePersistencePort.recordStart(normalizedEqpId, traceId, "UI start request already connected");
            log.info("Runtime start completed immediately (already connected). eqpId={}, traceId={}",
                    normalizedEqpId, traceId);
            return;
        }

        context.updateRuntimeState(EquipmentRuntimeState.CONNECTING, "EQP_START", traceId);

        if (equipmentInfo.connectionMode() == ConnectionMode.ACTIVE) {
            log.info("Active runtime start requested. eqpId={}, traceId={}", normalizedEqpId, traceId);
            connectionControlPort.resumeActiveReconnect(normalizedEqpId);
            connectionControlPort.connectActiveIfPossible(normalizedEqpId);
        } else {
            // PASSIVE는 서버가 이미 떠 있어도, PASS 판정은 실제 장비 채널 연결 기준으로 유지합니다.
            log.info("Passive runtime start requested. waiting for inbound connection. eqpId={}, traceId={}",
                    normalizedEqpId, traceId);
        }

        waitUntilConnected(normalizedEqpId, traceId, boundedTimeoutMs);
        context.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "EQP_START", traceId);
        statePersistencePort.recordStart(normalizedEqpId, traceId, "UI start request processed");

        log.info("Runtime start completed. eqpId={}, traceId={}, timeoutMs={}",
                normalizedEqpId, traceId, boundedTimeoutMs);
    }

    /**
     * END 요청을 처리합니다.
     *
     * <p>PASS 조건: timeout 내 실제 채널 해제(active=false) 확인</p>
     */
    public void endRuntime(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final long timeoutMs
    ) {
        final long boundedTimeoutMs = normalizeTimeoutMs(timeoutMs);
        final String normalizedEqpId = requireEqpId(eqpId);
        final CommInterfaceType requestedType = parseInterfaceType(interfaceType);

        final EquipmentContext context = resolveOrLoadContext(normalizedEqpId, traceId, "EQP_END");
        final GatewayEquipmentInfo equipmentInfo = context.profile().equipmentInfo();

        validateInterfaceType(equipmentInfo, requestedType);

        context.updateDesiredState(EquipmentDesiredState.ENDED, "EQP_END", traceId);
        log.info("Runtime end requested. eqpId={}, traceId={}", normalizedEqpId, traceId);

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

        // 채널 해제 이후 mailbox를 정리합니다.
        processingService.removeMailbox(normalizedEqpId);
        statePersistencePort.recordEnd(normalizedEqpId, traceId, "UI end request processed");

        log.info("Runtime end completed. eqpId={}, traceId={}, timeoutMs={}",
                normalizedEqpId, traceId, boundedTimeoutMs);
    }

    /**
     * DELETE 요청을 처리합니다.
     *
     * <p>STARTED 상태이거나 활성 채널이 존재하면 삭제를 거부합니다.</p>
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
                () -> new GatewayUiTaskProcessingException(
                        GatewayUiTaskErrorCode.EQP_CONTEXT_NOT_FOUND,
                        "Equipment context not found"
                )
        );
        validateInterfaceType(context.profile().equipmentInfo(), requestedType);

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

        statePersistencePort.recordDelete(normalizedEqpId, traceId, "UI delete request processed");
        log.info("Runtime context deleted. eqpId={}, traceId={}, timeoutMs={}",
                normalizedEqpId, traceId, timeoutMs);
    }

    /**
     * UI 전문 메시지를 command dispatcher로 전달합니다.
     *
     * <p>PASS 조건: 장비 채널 연결 상태 + 디스패치 경로 정상 완료</p>
     */
    public void sendUiMessage(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final String uiMessage,
            final long timeoutMs
    ) {
        if (uiMessage == null || uiMessage.isBlank()) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.UI_MESSAGE_REQUIRED,
                    "uiMessage is required"
            );
        }

        final String normalizedEqpId = requireEqpId(eqpId);
        final CommInterfaceType requestedType = parseInterfaceType(interfaceType);
        final EquipmentContext context = resolveOrLoadContext(normalizedEqpId, traceId, "EQP_SEND_MESSAGE");
        final GatewayEquipmentInfo equipmentInfo = context.profile().equipmentInfo();

        validateInterfaceType(equipmentInfo, requestedType);
        if (!equipmentInfo.enabled()) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.EQP_DISABLED,
                    "Equipment is disabled"
            );
        }
        if (context.desiredState() != EquipmentDesiredState.STARTED) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.EQP_NOT_STARTED,
                    "Equipment is not started"
            );
        }

        final EquipmentChannel channel = channelRegistry.get(new EquipmentId(normalizedEqpId));
        if (channel == null || !channel.isActive()) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.EQP_NOT_CONNECTED,
                    "Equipment channel is not connected"
            );
        }

        final String payloadBase64 = Base64.getEncoder().encodeToString(
                uiMessage.getBytes(StandardCharsets.UTF_8)
        );

        try {
            commandDispatcher.dispatch(new KafkaCommandMessage(
                    equipmentInfo.equipmentId(),
                    traceId,
                    equipmentInfo.commInterfaceType().name(),
                    equipmentInfo.socketType(),
                    payloadBase64,
                    Map.of(
                            "source", "TC-UI-BACKEND-APP",
                            "eventType", "EQP_SEND_MESSAGE"
                    )
            ));
        } catch (Exception ex) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.INTERNAL_ERROR,
                    "Failed to dispatch send-message command"
            );
        }

        if (log.isDebugEnabled()) {
            log.debug("UI message forwarded to command dispatcher. eqpId={}, traceId={}, timeoutMs={}",
                    normalizedEqpId, traceId, timeoutMs);
        }
    }

    /**
     * JARFILE 처리 전 공통 장비 검증 메서드입니다.
     */
    public GatewayEquipmentInfo resolveAndValidateEquipment(
            final String eqpId,
            final String interfaceType
    ) {
        final String normalizedEqpId = requireEqpId(eqpId);
        final CommInterfaceType requestedType = parseInterfaceType(interfaceType);
        final EquipmentContext context = resolveOrLoadContext(normalizedEqpId, null, "RESOLVE_VALIDATE");
        final GatewayEquipmentInfo equipmentInfo = context.profile().equipmentInfo();

        validateInterfaceType(equipmentInfo, requestedType);
        if (!equipmentInfo.enabled()) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.EQP_DISABLED,
                    "Equipment is disabled"
            );
        }

        if (log.isDebugEnabled()) {
            log.debug("UI runtime target validated. eqpId={}, interfaceType={}, connectionMode={}",
                    equipmentInfo.equipmentId(),
                    equipmentInfo.commInterfaceType(),
                    equipmentInfo.connectionMode());
        }
        return equipmentInfo;
    }

    /**
     * 기존 호출 호환용 ACTIVE 시작 메서드입니다.
     */
    public void startActiveIfNeeded(final GatewayEquipmentInfo equipmentInfo) {
        Objects.requireNonNull(equipmentInfo, "equipmentInfo is null");
        if (equipmentInfo.connectionMode() != ConnectionMode.ACTIVE) {
            if (log.isDebugEnabled()) {
                log.debug("Active start skipped (not ACTIVE mode). eqpId={}, mode={}",
                        equipmentInfo.equipmentId(), equipmentInfo.connectionMode());
            }
            return;
        }

        log.info("Active runtime start requested. eqpId={}", equipmentInfo.equipmentId());
        connectionControlPort.resumeActiveReconnect(equipmentInfo.equipmentId());
        connectionControlPort.connectActiveIfPossible(equipmentInfo.equipmentId());
    }

    /**
     * 기존 호출 호환용 END 메서드입니다.
     */
    public void stopRuntime(final String eqpId) {
        endRuntime(eqpId, resolveEquipmentInterface(eqpId), "LEGACY_STOP_RUNTIME", DEFAULT_LEGACY_TIMEOUT_MS);
    }

    /**
     * 컨텍스트를 조회하고 없으면 DB에서 즉시 로드합니다.
     */
    private EquipmentContext resolveOrLoadContext(
            final String eqpId,
            final String traceId,
            final String eventType
    ) {
        return contextRegistry.find(eqpId).orElseGet(() -> {
            final EquipmentContextProfile profile = profileProvider.findProfileById(eqpId).orElseThrow(
                    () -> new GatewayUiTaskProcessingException(
                            GatewayUiTaskErrorCode.EQP_NOT_FOUND,
                            "Equipment profile not found"
                    )
            );
            final GatewayEquipmentInfo info = profile.equipmentInfo();

            final EquipmentDesiredState desiredState = info.enabled()
                    ? EquipmentDesiredState.STARTED
                    : EquipmentDesiredState.ENDED;
            final EquipmentRuntimeState runtimeState = info.enabled()
                    ? EquipmentRuntimeState.DISCONNECTED
                    : EquipmentRuntimeState.REGISTERED;

            final EquipmentContext created = contextRegistry.upsertProfile(
                    profile,
                    desiredState,
                    runtimeState,
                    eventType,
                    traceId
            );
            if (log.isDebugEnabled()) {
                log.debug("Context loaded on demand. eqpId={}, eventType={}, traceId={}", eqpId, eventType, traceId);
            }
            return created;
        });
    }

    /**
     * eqpId 필수값을 검증하고 trim 결과를 반환합니다.
     */
    private String requireEqpId(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.EQP_ID_REQUIRED,
                    "eqpId is required"
            );
        }
        return eqpId.trim();
    }

    /**
     * interfaceType 문자열을 enum으로 변환합니다.
     */
    private CommInterfaceType parseInterfaceType(final String interfaceType) {
        try {
            return CommInterfaceType.fromText(interfaceType);
        } catch (Exception ex) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.INVALID_INTERFACE_TYPE,
                    "interfaceType is invalid"
            );
        }
    }

    /**
     * 요청 interfaceType과 장비 프로필 interfaceType 일치 여부를 검증합니다.
     */
    private void validateInterfaceType(
            final GatewayEquipmentInfo equipmentInfo,
            final CommInterfaceType requestedType
    ) {
        if (equipmentInfo.commInterfaceType() != requestedType) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.INTERFACE_MISMATCH,
                    "Requested interfaceType does not match equipment profile"
            );
        }
    }

    /**
     * 활성 채널 연결 상태를 확인합니다.
     */
    private boolean isChannelActive(final String eqpId) {
        final EquipmentChannel channel = channelRegistry.get(new EquipmentId(eqpId));
        return channel != null && channel.isActive();
    }

    /**
     * timeout 내 채널 연결 완료를 대기합니다.
     */
    private void waitUntilConnected(
            final String eqpId,
            final String traceId,
            final long timeoutMs
    ) {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            if (isChannelActive(eqpId)) {
                return;
            }
            sleepQuietly(WAIT_POLL_INTERVAL_MS);
        }

        contextRegistry.find(eqpId).ifPresent(context ->
                context.updateRuntimeState(EquipmentRuntimeState.DISCONNECTED, "EQP_START_TIMEOUT", traceId)
        );
        throw new GatewayUiTaskProcessingException(
                GatewayUiTaskErrorCode.EQP_START_TIMEOUT,
                "Timed out while waiting for equipment connection"
        );
    }

    /**
     * timeout 내 채널 해제 완료를 대기합니다.
     */
    private void waitUntilDisconnected(
            final String eqpId,
            final String traceId,
            final long timeoutMs
    ) {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            if (!isChannelActive(eqpId)) {
                return;
            }
            sleepQuietly(WAIT_POLL_INTERVAL_MS);
        }

        contextRegistry.find(eqpId).ifPresent(context ->
                context.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "EQP_END_TIMEOUT", traceId)
        );
        throw new GatewayUiTaskProcessingException(
                GatewayUiTaskErrorCode.EQP_END_TIMEOUT,
                "Timed out while waiting for equipment disconnection"
        );
    }

    /**
     * timeout 값을 보정합니다.
     */
    private long normalizeTimeoutMs(final long timeoutMs) {
        return timeoutMs <= 0L ? DEFAULT_LEGACY_TIMEOUT_MS : timeoutMs;
    }

    /**
     * 짧은 대기 유틸입니다.
     */
    private void sleepQuietly(final long ms) {
        if (ms <= 0L) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.INTERNAL_ERROR,
                    "Interrupted while waiting for runtime state transition"
            );
        }
    }

    /**
     * 레거시 stopRuntime 경로에서 interfaceType을 조회합니다.
     */
    private String resolveEquipmentInterface(final String eqpId) {
        try {
            return processingService.resolveEquipment(eqpId).commInterfaceType().name();
        } catch (Exception ex) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.EQP_NOT_FOUND,
                    "Equipment profile not found"
            );
        }
    }
}
