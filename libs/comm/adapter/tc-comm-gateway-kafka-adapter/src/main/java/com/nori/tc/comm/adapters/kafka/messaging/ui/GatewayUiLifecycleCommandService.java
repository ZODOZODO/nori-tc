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

/**
 * UI ?고????곹깭 ?꾩씠(START/END/DELETE)瑜??대떦?섎뒗 ?쒕퉬?ㅼ엯?덈떎.
 *
 * <p>Phase 2 ?댄썑 ?뺤콉:</p>
 * <p>1) 湲곕낯 寃쎈줈??鍮꾨룞湲??곹깭癒몄떊(EqpLifecycleStateMachine)?쇰줈 泥섎━</p>
 * <p>2) ?숆린 ?湲?寃쎈줈??feature flag濡?fallback ?좎?</p>
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
    private final GatewayUiTaskPolicyProperties uiTaskPolicyProperties;
    private final EqpLifecycleStateMachine lifecycleStateMachine;

    /**
     * ?곹깭 ?꾩씠 ?쒖뼱 ?섏〈?깆쓣 珥덇린?뷀빀?덈떎.
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
     * START ?붿껌??泥섎━?⑸땲??
     *
     * <p>syncWait=true(?숆린 fallback)???뚮쭔 timeout ???곌껐 ?꾨즺瑜?吏곸젒 ?湲고빀?덈떎.</p>
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

        if (log.isDebugEnabled()) {
            log.debug("Runtime start lifecycle policy resolved. eqpId={}, syncWaitEnabled={}",
                    normalizedEqpId,
                    uiTaskPolicyProperties.isLifecycleSyncWaitEnabled());
        }

        // Phase 2 湲곕낯 寃쎈줈: ?곹깭癒몄떊???붿껌???깅줉?섍퀬 利됱떆 ACCEPT ?⑸땲??
        if (!uiTaskPolicyProperties.isLifecycleSyncWaitEnabled()) {
            lifecycleStateMachine.requestStart(normalizedEqpId, traceId, boundedTimeoutMs);

            if (equipmentInfo.connectionMode() == ConnectionMode.ACTIVE) {
                connectionControlPort.resumeActiveReconnect(normalizedEqpId);
                connectionControlPort.connectActiveIfPossible(normalizedEqpId);
                log.info("LIFECYCLE_REQUEST_ACCEPTED. eqpId={}, transition=START, mode=ACTIVE, traceId={}, timeoutMs={}",
                        normalizedEqpId,
                        traceId,
                        boundedTimeoutMs);
            } else {
                log.info("LIFECYCLE_REQUEST_ACCEPTED. eqpId={}, transition=START, mode=PASSIVE, traceId={}, timeoutMs={}",
                        normalizedEqpId,
                        traceId,
                        boundedTimeoutMs);
            }
            return;
        }

        // ?숆린 fallback 寃쎈줈: 湲곗〈 諛⑹떇 ?좎?
        context.updateDesiredState(EquipmentDesiredState.STARTED, "EQP_START", traceId);

        if (isChannelActive(normalizedEqpId)) {
            context.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "EQP_START", traceId);
            contextCommandService.recordStartState(normalizedEqpId, traceId, "UI start request already connected");
            log.info("Runtime start completed immediately (already connected). eqpId={}, traceId={}",
                    normalizedEqpId,
                    traceId);
            return;
        }

        context.updateRuntimeState(EquipmentRuntimeState.CONNECTING, "EQP_START", traceId);

        if (equipmentInfo.connectionMode() == ConnectionMode.ACTIVE) {
            log.info("Active runtime start requested. eqpId={}, traceId={}", normalizedEqpId, traceId);
            connectionControlPort.resumeActiveReconnect(normalizedEqpId);
            connectionControlPort.connectActiveIfPossible(normalizedEqpId);
        } else {
            log.info("Passive runtime start requested. waiting for inbound connection. eqpId={}, traceId={}",
                    normalizedEqpId,
                    traceId);
        }

        waitUntilConnected(normalizedEqpId, traceId, boundedTimeoutMs);
        context.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "EQP_START", traceId);
        contextCommandService.recordStartState(normalizedEqpId, traceId, "UI start request processed");

        log.info("Runtime start completed. eqpId={}, traceId={}, timeoutMs={}",
                normalizedEqpId,
                traceId,
                boundedTimeoutMs);
    }

    /**
     * END ?붿껌??泥섎━?⑸땲??
     *
     * <p>syncWait=true(?숆린 fallback)???뚮쭔 timeout ??梨꾨꼸 ?댁젣 ?꾨즺瑜?吏곸젒 ?湲고빀?덈떎.</p>
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
            log.debug("Runtime end lifecycle policy resolved. eqpId={}, syncWaitEnabled={}",
                    normalizedEqpId,
                    uiTaskPolicyProperties.isLifecycleSyncWaitEnabled());
        }

        // Phase 2 湲곕낯 寃쎈줈: reconnect ?듭젣/梨꾨꼸 醫낅즺 ?붿껌 ???곹깭癒몄떊??END瑜??깅줉?⑸땲??
        if (!uiTaskPolicyProperties.isLifecycleSyncWaitEnabled()) {
            connectionControlPort.suppressActiveReconnect(normalizedEqpId);

            final EquipmentChannel channel = channelRegistry.get(new EquipmentId(normalizedEqpId));
            if (channel != null && channel.isActive()) {
                if (log.isDebugEnabled()) {
                    log.debug("Closing active channel by async runtime end. eqpId={}, traceId={}", normalizedEqpId, traceId);
                }
                channel.close();
            }

            lifecycleStateMachine.requestEnd(normalizedEqpId, traceId, boundedTimeoutMs);
            log.info("LIFECYCLE_REQUEST_ACCEPTED. eqpId={}, transition=END, mode={}, traceId={}, timeoutMs={}",
                    normalizedEqpId,
                    equipmentInfo.connectionMode(),
                    traceId,
                    boundedTimeoutMs);
            return;
        }

        // ?숆린 fallback 寃쎈줈: 湲곗〈 諛⑹떇 ?좎?
        context.updateDesiredState(EquipmentDesiredState.ENDED, "EQP_END", traceId);
        log.info("Runtime end requested. eqpId={}, traceId={}, mode={}",
                normalizedEqpId,
                traceId,
                equipmentInfo.connectionMode());

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
                normalizedEqpId,
                traceId,
                boundedTimeoutMs);
    }

    /**
     * DELETE ?붿껌??泥섎━?⑸땲??
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
     * legacy 寃쎈줈 ?명솚???꾪븳 ACTIVE ?쒖옉 ?쒖뼱?낅땲??
     */
    public void startActiveIfNeeded(final GatewayEquipmentInfo equipmentInfo) {
        Objects.requireNonNull(equipmentInfo, "equipmentInfo is null");
        if (equipmentInfo.connectionMode() != ConnectionMode.ACTIVE) {
            if (log.isDebugEnabled()) {
                log.debug("Active start skipped (not ACTIVE mode). eqpId={}, mode={}",
                        equipmentInfo.equipmentId(),
                        equipmentInfo.connectionMode());
            }
            return;
        }

        log.info("Active runtime start requested. eqpId={}", equipmentInfo.equipmentId());
        connectionControlPort.resumeActiveReconnect(equipmentInfo.equipmentId());
        connectionControlPort.connectActiveIfPossible(equipmentInfo.equipmentId());
    }

    /**
     * ?꾩옱 梨꾨꼸 active ?щ?瑜??뺤씤?⑸땲??
     */
    private boolean isChannelActive(final String eqpId) {
        final EquipmentChannel channel = channelRegistry.get(new EquipmentId(eqpId));
        return channel != null && channel.isActive();
    }

    /**
     * timeout ??梨꾨꼸 ?곌껐 ?꾨즺瑜??湲고빀?덈떎.
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
     * timeout ??梨꾨꼸 ?댁젣 ?꾨즺瑜??湲고빀?덈떎.
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
     * timeout ?낅젰媛믪쓣 蹂댁젙?⑸땲??
     */
    private long normalizeTimeoutMs(final long timeoutMs) {
        return timeoutMs <= 0L ? DEFAULT_TIMEOUT_MS : timeoutMs;
    }

    /**
     * 吏???쒓컙留뚰겮 ?湲고빀?덈떎.
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
