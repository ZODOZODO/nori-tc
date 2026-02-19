package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.adapters.kafka.messaging.GatewayCommandDispatcher;
import com.nori.tc.comm.adapters.kafka.messaging.contract.GatewayBusinessCommandMessage;
import com.nori.tc.comm.gateway.comm.EquipmentChannel;
import com.nori.tc.comm.gateway.comm.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.context.EquipmentContext;
import com.nori.tc.comm.gateway.context.EquipmentDesiredState;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/**
 * UI SEND_MESSAGE 이벤트를 전담하는 서비스입니다.
 *
 * <p>역할:
 * 1) 메시지/장비 상태 검증
 * 2) 신규 엔벨로프(metadata+data) 구성
 * 3) Gateway command dispatcher 호출</p>
 */
@Service
public class GatewayUiMessageCommandService {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiMessageCommandService.class);
    private static final String UI_SOURCE = "TC-UI-BACKEND-APP";
    private static final String SEND_MESSAGE_EVENT_TYPE = "EQP_SEND_MESSAGE";

    private final GatewayUiContextCommandService contextCommandService;
    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayCommandDispatcher commandDispatcher;

    /**
     * 메시지 전달 관련 의존성을 초기화합니다.
     */
    public GatewayUiMessageCommandService(
            final GatewayUiContextCommandService contextCommandService,
            final EquipmentChannelRegistry channelRegistry,
            final GatewayCommandDispatcher commandDispatcher
    ) {
        this.contextCommandService = Objects.requireNonNull(contextCommandService, "contextCommandService is null");
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry is null");
        this.commandDispatcher = Objects.requireNonNull(commandDispatcher, "commandDispatcher is null");
    }

    /**
     * UI 메시지를 command dispatcher로 전달합니다.
     *
     * <p>PASS 조건:
     * 장비가 started 상태이고 채널이 active=true이며 dispatch 예외가 없는 경우</p>
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

        final String normalizedEqpId = contextCommandService.requireEqpId(eqpId);
        final EquipmentContext context = contextCommandService.resolveOrLoadContext(
                normalizedEqpId,
                traceId,
                "EQP_SEND_MESSAGE"
        );
        final GatewayEquipmentInfo equipmentInfo = contextCommandService.resolveAndValidateEquipment(
                normalizedEqpId,
                interfaceType,
                traceId,
                "EQP_SEND_MESSAGE"
        );

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

        try {
            commandDispatcher.dispatchBusinessCommand(new GatewayBusinessCommandMessage(
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
            ));
        } catch (Exception ex) {
            log.warn("UI message command dispatch failed. eqpId={}, traceId={}, eventType={}",
                    normalizedEqpId,
                    traceId,
                    SEND_MESSAGE_EVENT_TYPE,
                    ex);
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
}
