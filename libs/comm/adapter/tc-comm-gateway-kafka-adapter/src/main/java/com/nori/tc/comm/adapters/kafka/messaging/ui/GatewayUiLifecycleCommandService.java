package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.gateway.comm.ConnectionMode;
import com.nori.tc.comm.gateway.comm.EquipmentChannel;
import com.nori.tc.comm.gateway.comm.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.comm.GatewayConnectionControlPort;
import com.nori.tc.comm.gateway.comm.GatewayProcessingService;
import com.nori.tc.comm.gateway.context.EquipmentContext;
import com.nori.tc.comm.gateway.context.EquipmentContextRegistry;
import com.nori.tc.comm.gateway.context.EquipmentDesiredState;
import com.nori.tc.comm.gateway.context.EquipmentRuntimeState;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * UI 런타임 상태 전이(START/END/DELETE)를 전담하는 서비스입니다.
 *
 * <p>역할:
 * 1) desired/runtime 상태 전이 관리
 * 2) active reconnect 제어
 * 3) 실제 채널 연결/해제 완료 대기
 * 4) 상태 이력(tc_eqp_state, tc_eqp_state_hist) 반영</p>
 */
@Service
public class GatewayUiLifecycleCommandService {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiLifecycleCommandService.class);

    private static final long WAIT_POLL_INTERVAL_MS = 100L;
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final GatewayUiContextCommandService contextCommandService;
    private final EquipmentContextRegistry contextRegistry;
    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayConnectionControlPort connectionControlPort;
    private final GatewayProcessingService processingService;

    /**
     * 상태 전이 제어 의존성을 초기화합니다.
     */
    public GatewayUiLifecycleCommandService(
            final GatewayUiContextCommandService contextCommandService,
            final EquipmentContextRegistry contextRegistry,
            final EquipmentChannelRegistry channelRegistry,
            final GatewayConnectionControlPort connectionControlPort,
            final GatewayProcessingService processingService
    ) {
        this.contextCommandService = Objects.requireNonNull(contextCommandService, "contextCommandService is null");
        this.contextRegistry = Objects.requireNonNull(contextRegistry, "contextRegistry is null");
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry is null");
        this.connectionControlPort = Objects.requireNonNull(connectionControlPort, "connectionControlPort is null");
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
    }

    /**
     * START 요청을 처리합니다.
     *
     * <p>PASS 조건:
     * timeout 내에 실제 채널이 active=true가 되는 경우</p>
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

        if (!equipmentInfo.enabled()) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.EQP_DISABLED,
                    "Equipment is disabled"
            );
        }

        context.updateDesiredState(EquipmentDesiredState.STARTED, "EQP_START", traceId);

        if (isChannelActive(normalizedEqpId)) {
            context.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "EQP_START", traceId);
            contextCommandService.recordStartState(normalizedEqpId, traceId, "UI start request already connected");
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
            log.info("Passive runtime start requested. waiting for inbound connection. eqpId={}, traceId={}",
                    normalizedEqpId, traceId);
        }

        waitUntilConnected(normalizedEqpId, traceId, boundedTimeoutMs);
        context.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "EQP_START", traceId);
        contextCommandService.recordStartState(normalizedEqpId, traceId, "UI start request processed");

        log.info("Runtime start completed. eqpId={}, traceId={}, timeoutMs={}",
                normalizedEqpId, traceId, boundedTimeoutMs);
    }

    /**
     * END 요청을 처리합니다.
     *
     * <p>PASS 조건:
     * timeout 내에 실제 채널이 active=false가 되는 경우</p>
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

        context.updateDesiredState(EquipmentDesiredState.ENDED, "EQP_END", traceId);
        log.info("Runtime end requested. eqpId={}, traceId={}, mode={}",
                normalizedEqpId, traceId, equipmentInfo.connectionMode());

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

        log.info("Runtime end completed. eqpId={}, traceId={}, timeoutMs={}",
                normalizedEqpId, traceId, boundedTimeoutMs);
    }

    /**
     * DELETE 요청을 처리합니다.
     *
     * <p>삭제 조건:
     * desiredState=ENDED 이고 active 채널이 없어야 합니다.</p>
     */
    public void deleteRuntimeContext(
            final String eqpId,
            final String interfaceType,
            final String traceId
    ) {
        final String normalizedEqpId = contextCommandService.requireEqpId(eqpId);
        final CommInterfaceType requestedType = contextCommandService.parseInterfaceType(interfaceType);

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
     * legacy 경로 호환을 위해 ACTIVE 시작 제어를 별도 노출합니다.
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
     * 현재 채널 active 여부를 확인합니다.
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
     * timeout 입력값을 보정합니다.
     */
    private long normalizeTimeoutMs(final long timeoutMs) {
        return timeoutMs <= 0L ? DEFAULT_TIMEOUT_MS : timeoutMs;
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
}
