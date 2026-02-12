package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.config.GatewayUiTaskPolicyProperties;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * UI 이벤트 타입별 실행 스펙 레지스트리입니다.
 *
 * <p>1차 최적화 목적:
 * - 이벤트가 늘어날 때마다 Handler 클래스를 추가하지 않고,
 *   "스펙 등록" 방식으로 확장 가능하도록 구조를 단순화합니다.</p>
 */
@Component
public class GatewayUiTaskProcessorRegistry {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskProcessorRegistry.class);

    private final Map<KafkaUiTaskEventType, GatewayUiTaskProcessorSpec> specsByType;
    private final GatewayUiJarfileTaskProcessor jarfileTaskProcessor;
    private final GatewayUiRuntimeControlService runtimeControlService;
    private final GatewayUiTaskPolicyProperties uiTaskPolicyProperties;

    /**
     * 이벤트 타입별 실행 스펙을 초기화합니다.
     */
    public GatewayUiTaskProcessorRegistry(
            final GatewayUiRuntimeControlService runtimeControlService,
            final GatewayUiTaskPolicyProperties uiTaskPolicyProperties,
            final ObjectProvider<GatewayUiJarfileTaskProcessor> jarfileTaskProcessorProvider
    ) {
        this.runtimeControlService = Objects.requireNonNull(runtimeControlService, "runtimeControlService is null");
        this.uiTaskPolicyProperties = Objects.requireNonNull(uiTaskPolicyProperties, "uiTaskPolicyProperties is null");
        this.jarfileTaskProcessor = jarfileTaskProcessorProvider.getIfAvailable(
                () -> (message, equipmentInfo) -> GatewayUiTaskResult.fail(
                        GatewayUiTaskErrorCode.JARFILE_TASK_NOT_CONFIGURED,
                        "Jarfile task processor is not configured"
                )
        );
        this.specsByType = buildSpecs();

        log.info("UI task processor registry initialized. count={}, eventTypes={}",
                specsByType.size(), specsByType.keySet());
    }

    /**
     * 이벤트 타입으로 실행 스펙을 조회합니다.
     */
    public Optional<GatewayUiTaskProcessorSpec> find(final KafkaUiTaskEventType eventType) {
        if (eventType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(specsByType.get(eventType));
    }

    /**
     * 이벤트 타입별 실행 스펙 맵을 구성합니다.
     */
    private Map<KafkaUiTaskEventType, GatewayUiTaskProcessorSpec> buildSpecs() {
        final Map<KafkaUiTaskEventType, GatewayUiTaskProcessorSpec> mapped = new EnumMap<>(KafkaUiTaskEventType.class);

        mapped.put(
                KafkaUiTaskEventType.EQP_CREATE,
                new GatewayUiTaskProcessorSpec(
                        KafkaUiTaskEventType.EQP_CREATE,
                        "EQP_CREATE_REP",
                        message -> executeRuntimeTask(
                                KafkaUiTaskEventType.EQP_CREATE,
                                message,
                                uiTaskPolicyProperties.getCreateTimeoutMs(),
                                () -> runtimeControlService.createOrUpdateContext(
                                        message.data().eqpId(),
                                        message.data().interfaceType(),
                                        message.metadata().traceId(),
                                        KafkaUiTaskEventType.EQP_CREATE.name(),
                                        uiTaskPolicyProperties.getCreateTimeoutMs()
                                ),
                                "Unhandled error while processing EQP_CREATE"
                        )
                )
        );
        mapped.put(
                KafkaUiTaskEventType.EQP_UPDATE,
                new GatewayUiTaskProcessorSpec(
                        KafkaUiTaskEventType.EQP_UPDATE,
                        "EQP_UPDATE_REP",
                        message -> executeRuntimeTask(
                                KafkaUiTaskEventType.EQP_UPDATE,
                                message,
                                uiTaskPolicyProperties.getUpdateTimeoutMs(),
                                () -> runtimeControlService.createOrUpdateContext(
                                        message.data().eqpId(),
                                        message.data().interfaceType(),
                                        message.metadata().traceId(),
                                        KafkaUiTaskEventType.EQP_UPDATE.name(),
                                        uiTaskPolicyProperties.getUpdateTimeoutMs()
                                ),
                                "Unhandled error while processing EQP_UPDATE"
                        )
                )
        );
        mapped.put(
                KafkaUiTaskEventType.EQP_DELETE,
                new GatewayUiTaskProcessorSpec(
                        KafkaUiTaskEventType.EQP_DELETE,
                        "EQP_DELETE_REP",
                        message -> executeRuntimeTask(
                                KafkaUiTaskEventType.EQP_DELETE,
                                message,
                                uiTaskPolicyProperties.getDeleteTimeoutMs(),
                                () -> runtimeControlService.deleteRuntimeContext(
                                        message.data().eqpId(),
                                        message.data().interfaceType(),
                                        message.metadata().traceId(),
                                        uiTaskPolicyProperties.getDeleteTimeoutMs()
                                ),
                                "Unhandled error while processing EQP_DELETE"
                        )
                )
        );
        mapped.put(
                KafkaUiTaskEventType.EQP_START,
                new GatewayUiTaskProcessorSpec(
                        KafkaUiTaskEventType.EQP_START,
                        "EQP_START_REP",
                        message -> executeRuntimeTask(
                                KafkaUiTaskEventType.EQP_START,
                                message,
                                uiTaskPolicyProperties.getStartTimeoutMs(),
                                () -> runtimeControlService.startRuntime(
                                        message.data().eqpId(),
                                        message.data().interfaceType(),
                                        message.metadata().traceId(),
                                        uiTaskPolicyProperties.getStartTimeoutMs()
                                ),
                                "Unhandled error while processing EQP_START"
                        )
                )
        );
        mapped.put(
                KafkaUiTaskEventType.EQP_END,
                new GatewayUiTaskProcessorSpec(
                        KafkaUiTaskEventType.EQP_END,
                        "EQP_END_REP",
                        message -> executeRuntimeTask(
                                KafkaUiTaskEventType.EQP_END,
                                message,
                                uiTaskPolicyProperties.getEndTimeoutMs(),
                                () -> runtimeControlService.endRuntime(
                                        message.data().eqpId(),
                                        message.data().interfaceType(),
                                        message.metadata().traceId(),
                                        uiTaskPolicyProperties.getEndTimeoutMs()
                                ),
                                "Unhandled error while processing EQP_END"
                        )
                )
        );
        mapped.put(
                KafkaUiTaskEventType.EQP_SEND_MESSAGE,
                new GatewayUiTaskProcessorSpec(
                        KafkaUiTaskEventType.EQP_SEND_MESSAGE,
                        "EQP_SEND_MESSAGE_REP",
                        message -> executeRuntimeTask(
                                KafkaUiTaskEventType.EQP_SEND_MESSAGE,
                                message,
                                uiTaskPolicyProperties.getSendMessageTimeoutMs(),
                                () -> runtimeControlService.sendUiMessage(
                                        message.data().eqpId(),
                                        message.data().interfaceType(),
                                        message.metadata().traceId(),
                                        message.data().uiMessage(),
                                        uiTaskPolicyProperties.getSendMessageTimeoutMs()
                                ),
                                "Unhandled error while processing EQP_SEND_MESSAGE"
                        )
                )
        );
        mapped.put(
                KafkaUiTaskEventType.EQP_UPDATE_JARFILE,
                new GatewayUiTaskProcessorSpec(
                        KafkaUiTaskEventType.EQP_UPDATE_JARFILE,
                        "EQP_UPDATE_JARFILE_REP",
                        this::executeJarfileTask
                )
        );

        return mapped;
    }

    /**
     * START/END/CREATE/UPDATE/DELETE/SEND_MESSAGE 공통 실행 래퍼입니다.
     */
    private GatewayUiTaskResult executeRuntimeTask(
            final KafkaUiTaskEventType eventType,
            final KafkaUiTaskMessage message,
            final long timeoutMs,
            final UiTaskAction action,
            final String unexpectedErrorMessage
    ) {
        if (log.isDebugEnabled()) {
            log.debug("UI {} task start. eqpId={}, traceId={}, timeoutMs={}",
                    eventType, message.data().eqpId(), message.metadata().traceId(), timeoutMs);
        }

        try {
            action.run();
            log.info("UI {} task success. eqpId={}, traceId={}",
                    eventType, message.data().eqpId(), message.metadata().traceId());
            return GatewayUiTaskResult.pass();
        } catch (GatewayUiTaskProcessingException ex) {
            log.warn("UI {} task failed. eqpId={}, traceId={}, errorCode={}",
                    eventType, message.data().eqpId(), message.metadata().traceId(), ex.errorCode());
            return GatewayUiTaskResult.fail(ex.errorCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("UI {} task failed by unexpected error. eqpId={}, traceId={}",
                    eventType, message.data().eqpId(), message.metadata().traceId(), ex);
            return GatewayUiTaskResult.fail(
                    GatewayUiTaskErrorCode.INTERNAL_ERROR,
                    unexpectedErrorMessage
            );
        }
    }

    /**
     * EQP_UPDATE_JARFILE 전용 실행 래퍼입니다.
     */
    private GatewayUiTaskResult executeJarfileTask(final KafkaUiTaskMessage message) {
        final long timeoutMs = uiTaskPolicyProperties.getUpdateJarfileTimeoutMs();
        if (log.isDebugEnabled()) {
            log.debug("UI {} task start. eqpId={}, traceId={}, timeoutMs={}",
                    KafkaUiTaskEventType.EQP_UPDATE_JARFILE,
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    timeoutMs);
        }

        final GatewayEquipmentInfo equipmentInfo;
        try {
            equipmentInfo = runtimeControlService.resolveAndValidateEquipment(
                    message.data().eqpId(),
                    message.data().interfaceType()
            );
        } catch (GatewayUiTaskProcessingException ex) {
            log.warn("UI {} task failed on validation. eqpId={}, traceId={}, errorCode={}",
                    KafkaUiTaskEventType.EQP_UPDATE_JARFILE,
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex.errorCode());
            return GatewayUiTaskResult.fail(ex.errorCode(), ex.getMessage());
        }

        try {
            final GatewayUiTaskResult result = jarfileTaskProcessor.process(message, equipmentInfo);
            if (result == null) {
                return GatewayUiTaskResult.fail(
                        GatewayUiTaskErrorCode.JARFILE_TASK_FAILED,
                        "Jarfile task returned null result"
                );
            }
            log.info("UI {} task finished. eqpId={}, traceId={}, status={}",
                    KafkaUiTaskEventType.EQP_UPDATE_JARFILE,
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    result.status());
            return result;
        } catch (Exception ex) {
            log.error("UI {} task failed by unexpected error. eqpId={}, traceId={}",
                    KafkaUiTaskEventType.EQP_UPDATE_JARFILE,
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex);
            return GatewayUiTaskResult.fail(
                    GatewayUiTaskErrorCode.JARFILE_TASK_FAILED,
                    "Jarfile task execution failed"
            );
        }
    }

    /**
     * 이벤트별 실행 스펙입니다.
     */
    public record GatewayUiTaskProcessorSpec(
            KafkaUiTaskEventType eventType,
            String replyEventType,
            GatewayUiTaskProcessor processor
    ) {
        public GatewayUiTaskProcessorSpec {
            Objects.requireNonNull(eventType, "eventType is null");
            Objects.requireNonNull(replyEventType, "replyEventType is null");
            Objects.requireNonNull(processor, "processor is null");
        }
    }

    /**
     * 이벤트 실행 함수형 계약입니다.
     */
    @FunctionalInterface
    public interface GatewayUiTaskProcessor {

        /**
         * UI task 메시지를 처리하고 PASS/FAIL 결과를 반환합니다.
         */
        GatewayUiTaskResult process(KafkaUiTaskMessage message);
    }

    /**
     * checked exception 전달을 허용하는 내부 실행 인터페이스입니다.
     */
    @FunctionalInterface
    private interface UiTaskAction {
        void run() throws Exception;
    }
}
