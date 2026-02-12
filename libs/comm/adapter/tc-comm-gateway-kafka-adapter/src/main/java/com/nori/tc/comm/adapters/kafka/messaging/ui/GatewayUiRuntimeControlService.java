package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * UI 런타임 제어 파사드 서비스입니다.
 *
 * <p>2차 리팩터링 이후 책임 분리:
 * 1) 컨텍스트/프로파일: {@link GatewayUiContextCommandService}
 * 2) 상태 전이(START/END/DELETE): {@link GatewayUiLifecycleCommandService}
 * 3) 메시지 전달(SEND_MESSAGE): {@link GatewayUiMessageCommandService}</p>
 *
 * <p>기존 호출 지점을 깨지 않기 위해 메서드 시그니처를 유지하면서 내부 구현만 위임합니다.</p>
 */
@Service
public class GatewayUiRuntimeControlService {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiRuntimeControlService.class);
    private static final long DEFAULT_LEGACY_TIMEOUT_MS = 30_000L;

    private final GatewayUiContextCommandService contextCommandService;
    private final GatewayUiLifecycleCommandService lifecycleCommandService;
    private final GatewayUiMessageCommandService messageCommandService;

    /**
     * 파사드가 위임할 하위 서비스들을 초기화합니다.
     */
    public GatewayUiRuntimeControlService(
            final GatewayUiContextCommandService contextCommandService,
            final GatewayUiLifecycleCommandService lifecycleCommandService,
            final GatewayUiMessageCommandService messageCommandService
    ) {
        this.contextCommandService = Objects.requireNonNull(contextCommandService, "contextCommandService is null");
        this.lifecycleCommandService = Objects.requireNonNull(lifecycleCommandService, "lifecycleCommandService is null");
        this.messageCommandService = Objects.requireNonNull(messageCommandService, "messageCommandService is null");
    }

    /**
     * CREATE/UPDATE 컨텍스트 갱신을 위임합니다.
     */
    public GatewayEquipmentInfo createOrUpdateContext(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final String eventType,
            final long timeoutMs
    ) {
        if (log.isDebugEnabled()) {
            log.debug("Delegating createOrUpdateContext. eventType={}, eqpId={}, traceId={}, timeoutMs={}",
                    eventType, eqpId, traceId, timeoutMs);
        }
        return contextCommandService.createOrUpdateContext(eqpId, interfaceType, traceId, eventType);
    }

    /**
     * START 처리를 위임합니다.
     */
    public void startRuntime(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final long timeoutMs
    ) {
        lifecycleCommandService.startRuntime(eqpId, interfaceType, traceId, timeoutMs);
    }

    /**
     * END 처리를 위임합니다.
     */
    public void endRuntime(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final long timeoutMs
    ) {
        lifecycleCommandService.endRuntime(eqpId, interfaceType, traceId, timeoutMs);
    }

    /**
     * DELETE 처리를 위임합니다.
     */
    public void deleteRuntimeContext(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final long timeoutMs
    ) {
        if (log.isDebugEnabled()) {
            log.debug("Delegating deleteRuntimeContext. eqpId={}, traceId={}, timeoutMs={}",
                    eqpId, traceId, timeoutMs);
        }
        lifecycleCommandService.deleteRuntimeContext(eqpId, interfaceType, traceId);
    }

    /**
     * SEND_MESSAGE 처리를 위임합니다.
     */
    public void sendUiMessage(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final String uiMessage,
            final long timeoutMs
    ) {
        messageCommandService.sendUiMessage(eqpId, interfaceType, traceId, uiMessage, timeoutMs);
    }

    /**
     * JARFILE 처리 전 공통 장비 검증을 위임합니다.
     */
    public GatewayEquipmentInfo resolveAndValidateEquipment(
            final String eqpId,
            final String interfaceType
    ) {
        return contextCommandService.resolveAndValidateEquipment(
                eqpId,
                interfaceType,
                "LEGACY_RESOLVE_VALIDATE",
                "RESOLVE_VALIDATE"
        );
    }

    /**
     * 기존 호환용 ACTIVE 시작 요청입니다.
     */
    public void startActiveIfNeeded(final GatewayEquipmentInfo equipmentInfo) {
        lifecycleCommandService.startActiveIfNeeded(equipmentInfo);
    }

    /**
     * 기존 호환용 STOP 경로입니다.
     */
    public void stopRuntime(final String eqpId) {
        final String interfaceType = contextCommandService.resolveInterfaceTypeName(eqpId);
        endRuntime(eqpId, interfaceType, "LEGACY_STOP_RUNTIME", DEFAULT_LEGACY_TIMEOUT_MS);
    }
}
