package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.gateway.comm.ConnectionMode;
import com.nori.tc.comm.gateway.comm.EquipmentChannel;
import com.nori.tc.comm.gateway.comm.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.comm.GatewayConnectionControlPort;
import com.nori.tc.comm.gateway.comm.GatewayProcessingService;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaCommandDispatcher;
import com.nori.tc.messaging.kafka.starter.contract.KafkaCommandMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * UI task 핸들러에서 공통으로 사용하는 런타임 제어 서비스입니다.
 *
 * <p>장비 검증, ACTIVE 연결 제어, runtime 정리, UI 원문 메시지 전송을
 * 한 곳으로 모아 핸들러 구현을 단순화합니다.</p>
 */
@Service
public class GatewayUiRuntimeControlService {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiRuntimeControlService.class);

    private final GatewayProcessingService processingService;
    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayConnectionControlPort connectionControlPort;
    private final KafkaCommandDispatcher commandDispatcher;

    /**
     * 공통 런타임 제어 의존성을 초기화합니다.
     */
    public GatewayUiRuntimeControlService(
            final GatewayProcessingService processingService,
            final EquipmentChannelRegistry channelRegistry,
            final GatewayConnectionControlPort connectionControlPort,
            final KafkaCommandDispatcher commandDispatcher
    ) {
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry is null");
        this.connectionControlPort = Objects.requireNonNull(connectionControlPort, "connectionControlPort is null");
        this.commandDispatcher = Objects.requireNonNull(commandDispatcher, "commandDispatcher is null");
    }

    /**
     * 장비 프로필을 조회하고 요청 인터페이스 타입/활성 상태를 검증합니다.
     */
    public GatewayEquipmentInfo resolveAndValidateEquipment(
            final String eqpId,
            final String interfaceType
    ) {
        final String normalizedEqpId = requireEqpId(eqpId);
        final CommInterfaceType requestedType = parseInterfaceType(interfaceType);
        final GatewayEquipmentInfo equipmentInfo = resolveEquipment(normalizedEqpId);

        if (equipmentInfo.commInterfaceType() != requestedType) {
            throw new GatewayUiTaskProcessingException(
                    "INTERFACE_MISMATCH",
                    "Requested interfaceType does not match equipment profile"
            );
        }
        if (!equipmentInfo.enabled()) {
            throw new GatewayUiTaskProcessingException(
                    "EQP_DISABLED",
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
     * 장비가 ACTIVE 모드일 때 연결/재연결을 재개하고 즉시 연결을 요청합니다.
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
     * 장비 단위 runtime 자원(재연결, 채널, mailbox)을 정리합니다.
     */
    public void stopRuntime(final String eqpId) {
        final String normalizedEqpId = requireEqpId(eqpId);
        log.info("Runtime stop requested. eqpId={}", normalizedEqpId);
        connectionControlPort.suppressActiveReconnect(normalizedEqpId);

        final EquipmentChannel channel = channelRegistry.get(new EquipmentId(normalizedEqpId));
        if (channel != null) {
            if (log.isDebugEnabled()) {
                log.debug("Closing active channel by runtime stop. eqpId={}", normalizedEqpId);
            }
            channel.close();
        }

        processingService.removeMailbox(normalizedEqpId);
    }

    /**
     * UI 원문 메시지를 기존 command dispatch 경로로 전달합니다.
     */
    public void sendUiMessage(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final String uiMessage
    ) {
        final GatewayEquipmentInfo equipmentInfo = resolveAndValidateEquipment(eqpId, interfaceType);
        if (uiMessage == null || uiMessage.isBlank()) {
            throw new GatewayUiTaskProcessingException("UI_MESSAGE_REQUIRED", "uiMessage is required");
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
            log.debug("UI message forwarded to command dispatcher. eqpId={}, traceId={}", eqpId, traceId);
        }
    }

    /**
     * GatewayProcessingService를 통해 장비 프로필을 조회합니다.
     */
    private GatewayEquipmentInfo resolveEquipment(final String eqpId) {
        try {
            return processingService.resolveEquipment(eqpId);
        } catch (Exception ex) {
            throw new GatewayUiTaskProcessingException("EQP_NOT_FOUND", "Equipment profile not found");
        }
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
     * 인터페이스 타입 문자열을 enum으로 변환합니다.
     */
    private CommInterfaceType parseInterfaceType(final String interfaceType) {
        try {
            return CommInterfaceType.fromText(interfaceType);
        } catch (Exception ex) {
            throw new GatewayUiTaskProcessingException("INVALID_INTERFACE_TYPE", "interfaceType is invalid");
        }
    }
}
