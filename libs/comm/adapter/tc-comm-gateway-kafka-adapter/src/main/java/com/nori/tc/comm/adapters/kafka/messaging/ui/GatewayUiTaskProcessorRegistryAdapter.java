package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.common.ui.task.pipeline.UiTaskProcessorRegistry;
import com.nori.tc.common.ui.task.pipeline.UiTaskProcessorSpec;
import com.nori.tc.common.ui.task.pipeline.UiTaskResult;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskReplyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Gateway 전용 처리기 레지스트리를 공통 UI 파이프라인 계약으로 변환하는 어댑터입니다.
 *
 * <p>기존 {@link GatewayUiTaskProcessorRegistry}의 이벤트별 처리 로직은 유지하고,
 * 공통 파이프라인이 요구하는 {@link UiTaskProcessorRegistry} 형태로만 노출합니다.</p>
 */
@Component
public class GatewayUiTaskProcessorRegistryAdapter implements UiTaskProcessorRegistry<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskProcessorRegistryAdapter.class);

    private final GatewayUiTaskProcessorRegistry delegate;
    private final Map<String, UiTaskProcessorSpec<KafkaUiTaskMessage>> specsByEventType;

    /**
     * 어댑터를 초기화합니다.
     *
     * @param delegate 기존 gateway 처리기 레지스트리
     */
    public GatewayUiTaskProcessorRegistryAdapter(final GatewayUiTaskProcessorRegistry delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is null");
        this.specsByEventType = buildSpecs(delegate);

        log.info("Gateway UI processor registry adapter initialized. count={}, eventTypes={}",
                specsByEventType.size(),
                specsByEventType.keySet());
    }

    /**
     * eventType으로 공통 처리기 스펙을 조회합니다.
     *
     * @param eventType 정규화된 이벤트 타입
     * @return 공통 처리기 스펙
     */
    @Override
    public Optional<UiTaskProcessorSpec<KafkaUiTaskMessage>> find(final String eventType) {
        final String normalizedEventType = normalize(eventType);
        if (normalizedEventType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(specsByEventType.get(normalizedEventType));
    }

    /**
     * 기존 enum 레지스트리를 공통 문자열 키 레지스트리로 변환합니다.
     *
     * @param delegate 기존 레지스트리
     * @return 공통 처리기 스펙 맵
     */
    private static Map<String, UiTaskProcessorSpec<KafkaUiTaskMessage>> buildSpecs(
            final GatewayUiTaskProcessorRegistry delegate
    ) {
        final Map<String, UiTaskProcessorSpec<KafkaUiTaskMessage>> mapped = new LinkedHashMap<>();
        for (KafkaUiTaskEventType eventType : KafkaUiTaskEventType.values()) {
            final Optional<GatewayUiTaskProcessorRegistry.GatewayUiTaskProcessorSpec> legacySpec = delegate.find(eventType);
            if (legacySpec.isEmpty()) {
                continue;
            }

            final GatewayUiTaskProcessorRegistry.GatewayUiTaskProcessorSpec spec = legacySpec.get();
            final String normalizedEventType = normalize(spec.eventType().name());
            mapped.put(
                    normalizedEventType,
                    new UiTaskProcessorSpec<>(
                            normalizedEventType,
                            spec.replyEventType(),
                            message -> toCommonResult(spec.processor().process(message))
                    )
            );
        }
        return Map.copyOf(mapped);
    }

    /**
     * gateway 결과 모델을 공통 결과 모델로 변환합니다.
     *
     * @param legacyResult 기존 gateway 결과
     * @return 공통 UI task 결과
     */
    private static UiTaskResult toCommonResult(final GatewayUiTaskResult legacyResult) {
        if (legacyResult == null) {
            return UiTaskResult.fail(
                    GatewayUiTaskErrorCode.INTERNAL_ERROR,
                    "UI task handler returned null result"
            );
        }
        if (legacyResult.status() == KafkaUiTaskReplyStatus.PASS) {
            return UiTaskResult.pass();
        }
        return UiTaskResult.fail(legacyResult.errorCode(), legacyResult.errorMessage());
    }

    /**
     * eventType 문자열을 공통 키 포맷(대문자)으로 정규화합니다.
     *
     * @param value 원본 eventType
     * @return 정규화된 eventType
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
}
