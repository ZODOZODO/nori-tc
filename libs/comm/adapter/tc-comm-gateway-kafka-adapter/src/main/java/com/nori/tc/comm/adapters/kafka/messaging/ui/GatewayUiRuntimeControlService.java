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
 * <p>- EquipmentContextRegistry 적재/갱신</p>
 * <p>- START/END/DELETE 상태 전이 처리</p>
 * <p>- tc_eqp_state/tc_eqp_state_hist 영속화 포트 호출</p>
 * <p>- UI 메시지 송신 전 실행 가능 상태 검증</p>
 */
@Service
public class GatewayUiRuntimeControlService {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiRuntimeControlService.class);

    private final GatewayProcessingService processingService;
    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayConnectionControlPort connectionControlPort;
    private final KafkaCommandDispatcher commandDispatcher;
    private final EquipmentContextRegistry contextRegistry;
    private final EquipmentContextProfileProvider profileProvider;
    private final EquipmentStatePersistencePort statePersistencePort;

    /**
     * 공통 런타임 제어 의존성을 초기화합니다.
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
     * <p>동작:</p>
     * <p>- DB에서 최신 프로파일 조회</p>
     * <p>- EquipmentContextRegistry에 upsert</p>
     * <p>- 상태 이력(OPER) 기록</p>
     */
    public GatewayEquipmentInfo createOrUpdateContext(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final String eventType
    ) {
        final String normalizedEqpId = requireEqpId(eqpId);
        final CommInterfaceType requestedType = parseInterfaceType(interfaceType);

        final EquipmentContextProfile profile = profileProvider.findProfileById(normalizedEqpId).orElseThrow(
                () -> new GatewayUiTaskProcessingException("EQP_NOT_FOUND", "Equipment profile not found")
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

        log.info("Context upsert completed. eventType={}, eqpId={}, traceId={}, enabled={}",
                eventType,
                normalizedEqpId,
                traceId,
                equipmentInfo.enabled());
        return equipmentInfo;
    }

    /**
     * START 요청을 처리합니다.
     *
     * <p>설계 합의사항:</p>
     * <p>- enabled=true일 때만 시작 허용</p>
     * <p>- control_state는 시작 판단에 사용하지 않음</p>
     */
    public void startRuntime(
            final String eqpId,
            final String interfaceType,
            final String traceId
    ) {
        final String normalizedEqpId = requireEqpId(eqpId);
        final CommInterfaceType requestedType = parseInterfaceType(interfaceType);

        final EquipmentContext context = resolveOrLoadContext(normalizedEqpId, traceId, "EQP_START");
        final GatewayEquipmentInfo equipmentInfo = context.profile().equipmentInfo();

        validateInterfaceType(equipmentInfo, requestedType);
        if (!equipmentInfo.enabled()) {
            throw new GatewayUiTaskProcessingException("EQP_DISABLED", "Equipment is disabled");
        }

        context.updateDesiredState(EquipmentDesiredState.STARTED, "EQP_START", traceId);
        context.updateRuntimeState(EquipmentRuntimeState.CONNECTING, "EQP_START", traceId);

        statePersistencePort.recordStart(normalizedEqpId, traceId, "UI start request processed");

        if (equipmentInfo.connectionMode() == ConnectionMode.ACTIVE) {
            log.info("Active runtime start requested. eqpId={}, traceId={}", normalizedEqpId, traceId);
            connectionControlPort.resumeActiveReconnect(normalizedEqpId);
            connectionControlPort.connectActiveIfPossible(normalizedEqpId);
        } else {
            // PASSIVE는 서버가 상시 구동 중이므로 "수신 허용 상태"로만 전이합니다.
            context.updateRuntimeState(EquipmentRuntimeState.DISCONNECTED, "EQP_START", traceId);
            log.info("Passive runtime marked as started. eqpId={}, traceId={}", normalizedEqpId, traceId);
        }
    }

    /**
     * END 요청을 처리합니다.
     */
    public void endRuntime(
            final String eqpId,
            final String interfaceType,
            final String traceId
    ) {
        final String normalizedEqpId = requireEqpId(eqpId);
        final CommInterfaceType requestedType = parseInterfaceType(interfaceType);

        final EquipmentContext context = resolveOrLoadContext(normalizedEqpId, traceId, "EQP_END");
        final GatewayEquipmentInfo equipmentInfo = context.profile().equipmentInfo();

        validateInterfaceType(equipmentInfo, requestedType);

        context.updateDesiredState(EquipmentDesiredState.ENDED, "EQP_END", traceId);
        context.updateRuntimeState(EquipmentRuntimeState.DISCONNECTED, "EQP_END", traceId);

        statePersistencePort.recordEnd(normalizedEqpId, traceId, "UI end request processed");

        log.info("Runtime end requested. eqpId={}, traceId={}", normalizedEqpId, traceId);
        connectionControlPort.suppressActiveReconnect(normalizedEqpId);

        final EquipmentChannel channel = channelRegistry.get(new EquipmentId(normalizedEqpId));
        if (channel != null) {
            if (log.isDebugEnabled()) {
                log.debug("Closing active channel by runtime end. eqpId={}, traceId={}", normalizedEqpId, traceId);
            }
            channel.close();
        }

        // mailbox는 송수신 큐를 포함하므로 END 즉시 정리합니다.
        processingService.removeMailbox(normalizedEqpId);
    }

    /**
     * DELETE 요청을 처리합니다.
     *
     * <p>정책:</p>
     * <p>- 현재 STARTED 상태이거나 활성 채널이 있으면 삭제 거부</p>
     * <p>- STOPPED(ENDED) 상태에서만 메모리 컨텍스트 제거</p>
     */
    public void deleteRuntimeContext(
            final String eqpId,
            final String interfaceType,
            final String traceId
    ) {
        final String normalizedEqpId = requireEqpId(eqpId);
        final CommInterfaceType requestedType = parseInterfaceType(interfaceType);

        final EquipmentContext context = contextRegistry.find(normalizedEqpId).orElseThrow(
                () -> new GatewayUiTaskProcessingException("EQP_CONTEXT_NOT_FOUND", "Equipment context not found")
        );
        validateInterfaceType(context.profile().equipmentInfo(), requestedType);

        final EquipmentChannel channel = channelRegistry.get(new EquipmentId(normalizedEqpId));
        if (context.desiredState() == EquipmentDesiredState.STARTED || (channel != null && channel.isActive())) {
            throw new GatewayUiTaskProcessingException("EQP_RUNNING", "Equipment must be ended before delete");
        }

        connectionControlPort.suppressActiveReconnect(normalizedEqpId);
        processingService.removeMailbox(normalizedEqpId);
        contextRegistry.remove(normalizedEqpId, "EQP_DELETE", traceId);

        statePersistencePort.recordDelete(normalizedEqpId, traceId, "UI delete request processed");
        log.info("Runtime context deleted. eqpId={}, traceId={}", normalizedEqpId, traceId);
    }

    /**
     * UI 전문 메시지를 명령 디스패처 경로로 전달합니다.
     */
    public void sendUiMessage(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final String uiMessage
    ) {
        if (uiMessage == null || uiMessage.isBlank()) {
            throw new GatewayUiTaskProcessingException("UI_MESSAGE_REQUIRED", "uiMessage is required");
        }

        final String normalizedEqpId = requireEqpId(eqpId);
        final CommInterfaceType requestedType = parseInterfaceType(interfaceType);
        final EquipmentContext context = resolveOrLoadContext(normalizedEqpId, traceId, "EQP_SEND_MESSAGE");
        final GatewayEquipmentInfo equipmentInfo = context.profile().equipmentInfo();

        validateInterfaceType(equipmentInfo, requestedType);
        if (!equipmentInfo.enabled()) {
            throw new GatewayUiTaskProcessingException("EQP_DISABLED", "Equipment is disabled");
        }
        if (context.desiredState() != EquipmentDesiredState.STARTED) {
            throw new GatewayUiTaskProcessingException("EQP_NOT_STARTED", "Equipment is not started");
        }

        final EquipmentChannel channel = channelRegistry.get(new EquipmentId(normalizedEqpId));
        if (channel == null || !channel.isActive()) {
            throw new GatewayUiTaskProcessingException("EQP_NOT_CONNECTED", "Equipment channel is not connected");
        }

        final String payloadBase64 = Base64.getEncoder().encodeToString(
                uiMessage.getBytes(StandardCharsets.UTF_8)
        );

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

        if (log.isDebugEnabled()) {
            log.debug("UI message forwarded to command dispatcher. eqpId={}, traceId={}", normalizedEqpId, traceId);
        }
    }

    /**
     * 기존 핸들러와 호환을 위해 유지하는 검증 메서드입니다.
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
            throw new GatewayUiTaskProcessingException("EQP_DISABLED", "Equipment is disabled");
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
     * START 호환 메서드입니다.
     *
     * <p>기존 호출 경로를 깨지 않기 위해 유지하며, 내부적으로 기존 동작을 수행합니다.</p>
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
     * END 호환 메서드입니다.
     */
    public void stopRuntime(final String eqpId) {
        endRuntime(eqpId, resolveEquipmentInterface(eqpId), "LEGACY_STOP_RUNTIME");
    }

    /**
     * 컨텍스트를 조회하고 없으면 DB에서 적재합니다.
     */
    private EquipmentContext resolveOrLoadContext(
            final String eqpId,
            final String traceId,
            final String eventType
    ) {
        return contextRegistry.find(eqpId).orElseGet(() -> {
            final EquipmentContextProfile profile = profileProvider.findProfileById(eqpId).orElseThrow(
                    () -> new GatewayUiTaskProcessingException("EQP_NOT_FOUND", "Equipment profile not found")
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
            throw new GatewayUiTaskProcessingException("EQP_ID_REQUIRED", "eqpId is required");
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
            throw new GatewayUiTaskProcessingException("INVALID_INTERFACE_TYPE", "interfaceType is invalid");
        }
    }

    /**
     * 요청 인터페이스와 설비 프로파일 인터페이스가 일치하는지 검증합니다.
     */
    private void validateInterfaceType(
            final GatewayEquipmentInfo equipmentInfo,
            final CommInterfaceType requestedType
    ) {
        if (equipmentInfo.commInterfaceType() != requestedType) {
            throw new GatewayUiTaskProcessingException(
                    "INTERFACE_MISMATCH",
                    "Requested interfaceType does not match equipment profile"
            );
        }
    }

    /**
     * 레거시 stopRuntime 경로에서 사용할 interfaceType을 조회합니다.
     */
    private String resolveEquipmentInterface(final String eqpId) {
        try {
            return processingService.resolveEquipment(eqpId).commInterfaceType().name();
        } catch (Exception ex) {
            // stopRuntime은 기존 호출 호환 경로이므로, 실패 시 SOCKET 기본값으로 처리하지 않고 예외를 유지합니다.
            throw new GatewayUiTaskProcessingException("EQP_NOT_FOUND", "Equipment profile not found");
        }
    }
}
