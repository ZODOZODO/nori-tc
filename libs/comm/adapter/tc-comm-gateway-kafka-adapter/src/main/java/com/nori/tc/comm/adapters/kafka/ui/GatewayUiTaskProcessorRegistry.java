package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.config.GatewayUiTaskPolicyProperties;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.socket.plugin.GatewaySocketPluginRuntimeMutationPort;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskProcessorRegistry;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskProcessorSpec;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskResult;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Gateway UI task 처리기 레지스트리입니다.
 *
 * <p>핵심 설계 원칙:</p>
 * <p>1) 이벤트 타입별 분기 규칙은 이 클래스에서 단일 관리합니다.</p>
 * <p>2) 공통 파이프라인 계약({@link KafkaTaskProcessorRegistry})을 직접 구현해
 *    중간 브리지 어댑터 클래스를 제거합니다.</p>
 * <p>3) 처리 결과는 공통 결과 모델({@link KafkaTaskResult})로 통일합니다.</p>
 */
@Component
public class GatewayUiTaskProcessorRegistry implements KafkaTaskProcessorRegistry<KafkaUiTaskMessage> {

    /**
     * 런타임 처리 로그를 출력하는 로거입니다.
     */
    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskProcessorRegistry.class);

    /**
     * eventType(대문자) -> 처리 스펙 매핑입니다.
     */
    private final Map<String, KafkaTaskProcessorSpec<KafkaUiTaskMessage>> specsByEventType;

    /**
     * 플러그인 런타임 변경 포트입니다(EQP_UPDATE_JARFILE 전용).
     */
    private final GatewaySocketPluginRuntimeMutationPort pluginRuntimeMutationPort;

    /**
     * 플러그인 런타임 변경 포트가 실제 구성되었는지 여부입니다.
     */
    private final boolean pluginRuntimeMutationConfigured;

    /**
     * 런타임 컨텍스트 생성/종료를 담당하는 서비스입니다.
     */
    private final GatewayUiRuntimeControlService runtimeControlService;

    /**
     * UI task 정책 프로퍼티입니다.
     */
    private final GatewayUiTaskPolicyProperties uiTaskPolicyProperties;

    /**
     * 레지스트리를 초기화합니다.
     *
     * @param runtimeControlService 런타임 제어 서비스
     * @param uiTaskPolicyProperties UI task 정책
     * @param pluginRuntimeMutationPortProvider 플러그인 런타임 변경 포트 제공자
     */
    public GatewayUiTaskProcessorRegistry(
            final GatewayUiRuntimeControlService runtimeControlService,
            final GatewayUiTaskPolicyProperties uiTaskPolicyProperties,
            final ObjectProvider<GatewaySocketPluginRuntimeMutationPort> pluginRuntimeMutationPortProvider
    ) {
        this.runtimeControlService = Objects.requireNonNull(runtimeControlService, "runtimeControlService is null");
        this.uiTaskPolicyProperties = Objects.requireNonNull(uiTaskPolicyProperties, "uiTaskPolicyProperties is null");

        final GatewaySocketPluginRuntimeMutationPort resolvedPort = pluginRuntimeMutationPortProvider.getIfAvailable();
        this.pluginRuntimeMutationConfigured = resolvedPort != null;
        this.pluginRuntimeMutationPort = resolvedPort == null
                ? GatewaySocketPluginRuntimeMutationPort.noop()
                : resolvedPort;
        this.specsByEventType = Map.copyOf(buildSpecs());

        log.info("Gateway UI task processor registry initialized. count={}, eventTypes={}, pluginMutationConfigured={}",
                specsByEventType.size(),
                specsByEventType.keySet(),
                pluginRuntimeMutationConfigured);
        if (!pluginRuntimeMutationConfigured) {
            log.info("Gateway socket plugin runtime mutation port is not configured. EQP_UPDATE_JARFILE requests will fail.");
        }
    }

    /**
     * eventType으로 처리 스펙을 조회합니다.
     *
     * @param eventType 정규화 전 eventType
     * @return 처리 스펙(Optional)
     */
    @Override
    public Optional<KafkaTaskProcessorSpec<KafkaUiTaskMessage>> find(final String eventType) {
        final String normalizedEventType = normalize(eventType);
        if (normalizedEventType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(specsByEventType.get(normalizedEventType));
    }

    /**
     * 이벤트 타입별 처리 스펙 맵을 생성합니다.
     */
    private Map<String, KafkaTaskProcessorSpec<KafkaUiTaskMessage>> buildSpecs() {
        final Map<String, KafkaTaskProcessorSpec<KafkaUiTaskMessage>> mapped = new LinkedHashMap<>();

        register(
                mapped,
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
        );

        register(
                mapped,
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
        );

        register(
                mapped,
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
        );

        register(
                mapped,
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
        );

        register(
                mapped,
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
        );

        register(
                mapped,
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
        );

        register(
                mapped,
                KafkaUiTaskEventType.EQP_UPDATE_JARFILE,
                "EQP_UPDATE_JARFILE_REP",
                this::executeJarfileTask
        );

        return mapped;
    }

    /**
     * 단일 eventType 처리 스펙을 등록합니다.
     */
    private void register(
            final Map<String, KafkaTaskProcessorSpec<KafkaUiTaskMessage>> mapped,
            final KafkaUiTaskEventType eventType,
            final String replyEventType,
            final GatewayUiTaskProcessor processor
    ) {
        final String key = eventType.name();
        final KafkaTaskProcessorSpec<KafkaUiTaskMessage> spec = new KafkaTaskProcessorSpec<>(
                key,
                replyEventType,
                processor::process
        );
        if (mapped.putIfAbsent(key, spec) != null) {
            throw new IllegalStateException("Duplicate Gateway UI task processor eventType: " + key);
        }
    }

    /**
     * CREATE/UPDATE/DELETE/START/END/SEND_MESSAGE 공통 실행 로직입니다.
     */
    private KafkaTaskResult executeRuntimeTask(
            final KafkaUiTaskEventType eventType,
            final KafkaUiTaskMessage message,
            final long timeoutMs,
            final RuntimeControlAction action,
            final String unexpectedErrorMessage
    ) {
        if (log.isDebugEnabled()) {
            log.debug("UI {} task start. eqpId={}, traceId={}, timeoutMs={}",
                    eventType,
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    timeoutMs);
        }

        try {
            action.run();
            log.info("UI {} task success. eqpId={}, traceId={}",
                    eventType,
                    message.data().eqpId(),
                    message.metadata().traceId());
            return KafkaTaskResult.pass();
        } catch (GatewayUiTaskProcessingException ex) {
            log.warn("UI {} task failed. eqpId={}, traceId={}, errorCode={}",
                    eventType,
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex.errorCode());
            return KafkaTaskResult.fail(ex.errorCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("UI {} task failed by unexpected error. eqpId={}, traceId={}",
                    eventType,
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex);
            return KafkaTaskResult.fail(
                    GatewayUiTaskErrorCode.INTERNAL_ERROR,
                    unexpectedErrorMessage
            );
        }
    }

    /**
     * EQP_UPDATE_JARFILE 이벤트를 처리합니다.
     */
    private KafkaTaskResult executeJarfileTask(final KafkaUiTaskMessage message) {
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
            return KafkaTaskResult.fail(ex.errorCode(), ex.getMessage());
        }

        if (!pluginRuntimeMutationConfigured) {
            log.warn("UI {} task rejected. eqpId={}, traceId={}, reason=plugin runtime mutation port is not configured",
                    KafkaUiTaskEventType.EQP_UPDATE_JARFILE,
                    message.data().eqpId(),
                    message.metadata().traceId());
            return KafkaTaskResult.fail(
                    GatewayUiTaskErrorCode.JARFILE_TASK_NOT_CONFIGURED,
                    "Gateway socket plugin runtime mutation port is not configured"
            );
        }

        try {
            pluginRuntimeMutationPort.reloadByEqpId(equipmentInfo.equipmentId());
            log.info("UI {} task finished. eqpId={}, traceId={}, status={}",
                    KafkaUiTaskEventType.EQP_UPDATE_JARFILE,
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    "PASS");
            return KafkaTaskResult.pass();
        } catch (Exception ex) {
            log.error("UI {} task failed by unexpected error. eqpId={}, traceId={}",
                    KafkaUiTaskEventType.EQP_UPDATE_JARFILE,
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex);
            return KafkaTaskResult.fail(
                    GatewayUiTaskErrorCode.JARFILE_TASK_FAILED,
                    "Jarfile task execution failed: " + safeMessage(ex)
            );
        }
    }

    /**
     * 예외 메시지를 null-safe 문자열로 변환합니다.
     */
    private static String safeMessage(final Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        final String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }

    /**
     * eventType 문자열을 공통 키 포맷(대문자)으로 정규화합니다.
     */
    private static String normalize(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase();
    }

    /**
     * UI task 처리 함수형 인터페이스입니다.
     */
    @FunctionalInterface
    private interface GatewayUiTaskProcessor {
        KafkaTaskResult process(KafkaUiTaskMessage message);
    }

    /**
     * checked exception 전파를 허용하는 내부 실행 인터페이스입니다.
     */
    @FunctionalInterface
    private interface RuntimeControlAction {
        void run() throws Exception;
    }
}
