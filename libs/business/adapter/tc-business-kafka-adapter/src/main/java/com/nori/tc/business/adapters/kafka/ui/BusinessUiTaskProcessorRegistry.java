package com.nori.tc.business.adapters.kafka.ui;

import com.nori.tc.business.core.ui.BusinessUiTaskErrorCode;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskProcessorRegistry;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskProcessorSpec;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskReplyStatus;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskResult;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Business UI Task eventType별 처리기 레지스트리입니다.
 *
 * <p>구성 목표:</p>
 * <p>1) Gateway UI 레지스트리와 동일한 제어 흐름(매핑/공통 실행 래퍼/표준 로깅)을 유지합니다.</p>
 * <p>2) 실제 비즈니스 동작은 {@link BusinessUiModelRuntimeCommandService} 메서드 내부 구현으로 위임합니다.</p>
 * <p>3) 결과 반환 형식은 공통 파이프라인 계약({@link KafkaTaskResult})을 그대로 사용합니다.</p>
 */
@Component
public class BusinessUiTaskProcessorRegistry implements KafkaTaskProcessorRegistry<KafkaUiTaskMessage> {

    /**
     * 레지스트리 진단 로그 출력을 위한 로거입니다.
     */
    private static final Logger log = LoggerFactory.getLogger(BusinessUiTaskProcessorRegistry.class);

    /**
     * eventType -> processor 매핑 저장소입니다.
     */
    private final Map<String, KafkaTaskProcessorSpec<KafkaUiTaskMessage>> specsByEventType;

    /**
     * 실제 UI 도메인 처리 로직 서비스입니다.
     */
    private final BusinessUiModelRuntimeCommandService commandService;

    /**
     * 레지스트리를 초기화하고 eventType 매핑을 구성합니다.
     *
     * @param commandService UI 명령 처리 서비스
     */
    public BusinessUiTaskProcessorRegistry(final BusinessUiModelRuntimeCommandService commandService) {
        this.commandService = Objects.requireNonNull(commandService, "commandService is null");
        this.specsByEventType = Map.copyOf(buildSpecs());

        log.info("Business UI task processor registry initialized. count={}, eventTypes={}",
                specsByEventType.size(),
                specsByEventType.keySet());
    }

    /**
     * eventType에 대응하는 처리기 스펙을 조회합니다.
     *
     * @param eventType 조회 대상 eventType
     * @return 처리기 스펙(optional)
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
     * 지원하는 eventType 처리기 스펙을 구성합니다.
     *
     * @return 불변 맵 생성 전의 가변 매핑
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
                        () -> commandService.handleEqpCreateOrUpdate(message)
                )
        );

        register(
                mapped,
                KafkaUiTaskEventType.EQP_UPDATE,
                "EQP_UPDATE_REP",
                message -> executeRuntimeTask(
                        KafkaUiTaskEventType.EQP_UPDATE,
                        message,
                        () -> commandService.handleEqpCreateOrUpdate(message)
                )
        );

        register(
                mapped,
                KafkaUiTaskEventType.EQP_DELETE,
                "EQP_DELETE_REP",
                message -> executeRuntimeTask(
                        KafkaUiTaskEventType.EQP_DELETE,
                        message,
                        () -> commandService.handleEqpDelete(message)
                )
        );

        register(
                mapped,
                KafkaUiTaskEventType.EQP_UPDATE_JARFILE,
                "EQP_UPDATE_JARFILE_REP",
                message -> executeRuntimeTask(
                        KafkaUiTaskEventType.EQP_UPDATE_JARFILE,
                        message,
                        () -> commandService.handleEqpUpdateJarfile(message)
                )
        );

        return mapped;
    }

    /**
     * 단일 eventType 처리기 스펙을 등록합니다.
     *
     * @param mapped 등록 대상 맵
     * @param eventType 이벤트 타입
     * @param replyEventType 응답 이벤트 타입
     * @param processor 처리 람다
     */
    private void register(
            final Map<String, KafkaTaskProcessorSpec<KafkaUiTaskMessage>> mapped,
            final KafkaUiTaskEventType eventType,
            final String replyEventType,
            final BusinessUiTaskProcessor processor
    ) {
        final String key = eventType.name();
        final KafkaTaskProcessorSpec<KafkaUiTaskMessage> spec = new KafkaTaskProcessorSpec<>(
                key,
                replyEventType,
                processor::process
        );
        if (mapped.putIfAbsent(key, spec) != null) {
            throw new IllegalStateException("Duplicate UI task processor eventType: " + key);
        }
    }

    /**
     * 공통 런타임 실행 래퍼입니다.
     *
     * <p>Gateway 레지스트리와 동일하게, 이 메서드가 다음 책임을 표준화합니다.</p>
     * <p>1) 시작/완료/실패 로그 포맷 통일</p>
     * <p>2) 예기치 못한 예외의 fail 응답 변환</p>
     *
     * @param eventType 실행 이벤트 타입
     * @param message 원본 UI 메시지
     * @param action 실제 처리 로직
     * @return 표준 처리 결과
     */
    private KafkaTaskResult executeRuntimeTask(
            final KafkaUiTaskEventType eventType,
            final KafkaUiTaskMessage message,
            final RuntimeControlAction action
    ) {
        final String eqpId = message == null || message.data() == null ? null : message.data().eqpId();
        final String traceId = message == null || message.metadata() == null ? null : message.metadata().traceId();

        if (log.isDebugEnabled()) {
            log.debug("UI {} task start. eqpId={}, traceId={}", eventType, eqpId, traceId);
        }

        try {
            final KafkaTaskResult result = action.run();
            if (result.status() == KafkaTaskReplyStatus.PASS) {
                log.info("UI {} task success. eqpId={}, traceId={}", eventType, eqpId, traceId);
                return result;
            }

            log.warn("UI {} task failed. eqpId={}, traceId={}, errorCode={}",
                    eventType,
                    eqpId,
                    traceId,
                    result.errorCode());
            return result;
        } catch (Exception ex) {
            log.error("UI {} task failed by unexpected error. eqpId={}, traceId={}", eventType, eqpId, traceId, ex);
            return KafkaTaskResult.fail(
                    BusinessUiTaskErrorCode.INTERNAL_ERROR,
                    "Unhandled error while processing " + eventType.name()
            );
        }
    }

    /**
     * eventType 문자열을 정규화합니다.
     *
     * @param value 원본 문자열
     * @return trim + upper-case 결과(빈 문자열이면 null)
     */
    private static String normalize(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toUpperCase();
    }

    /**
     * 단일 UI task 처리기 함수형 인터페이스입니다.
     */
    @FunctionalInterface
    private interface BusinessUiTaskProcessor {
        KafkaTaskResult process(KafkaUiTaskMessage message);
    }

    /**
     * 공통 실행 래퍼에서 사용할 런타임 제어 액션 인터페이스입니다.
     */
    @FunctionalInterface
    private interface RuntimeControlAction {
        KafkaTaskResult run() throws Exception;
    }
}
