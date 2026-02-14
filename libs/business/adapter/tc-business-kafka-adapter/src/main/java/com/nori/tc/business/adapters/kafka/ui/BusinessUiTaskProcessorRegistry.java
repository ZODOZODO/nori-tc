package com.nori.tc.business.adapters.kafka.ui;

import com.nori.tc.common.ui.task.pipeline.UiTaskProcessorRegistry;
import com.nori.tc.common.ui.task.pipeline.UiTaskProcessorSpec;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Business UI task processor 레지스트리입니다.
 *
 * <p>eventType별 처리기와 replyEventType 매핑을 관리합니다.</p>
 */
@Component
public class BusinessUiTaskProcessorRegistry implements UiTaskProcessorRegistry<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(BusinessUiTaskProcessorRegistry.class);

    private final Map<String, UiTaskProcessorSpec<KafkaUiTaskMessage>> specsByEventType;

    /**
     * 레지스트리를 초기화합니다.
     *
     * @param commandService model runtime 제어 서비스
     */
    public BusinessUiTaskProcessorRegistry(final BusinessUiModelRuntimeCommandService commandService) {
        Objects.requireNonNull(commandService, "commandService is null");

        final Map<String, UiTaskProcessorSpec<KafkaUiTaskMessage>> mapped = new LinkedHashMap<>();
        register(
                mapped,
                new UiTaskProcessorSpec<>(
                        "EQP_CREATE",
                        "EQP_CREATE_REP",
                        commandService::handleEqpCreateOrUpdate
                )
        );
        register(
                mapped,
                new UiTaskProcessorSpec<>(
                        "EQP_UPDATE",
                        "EQP_UPDATE_REP",
                        commandService::handleEqpCreateOrUpdate
                )
        );
        register(
                mapped,
                new UiTaskProcessorSpec<>(
                        "EQP_UPDATE_JARFILE",
                        "EQP_UPDATE_JARFILE_REP",
                        commandService::handleEqpUpdateJarfile
                )
        );
        this.specsByEventType = Map.copyOf(mapped);

        log.info("Business UI task processor registry initialized. count={}, eventTypes={}",
                specsByEventType.size(),
                specsByEventType.keySet());
    }

    @Override
    public Optional<UiTaskProcessorSpec<KafkaUiTaskMessage>> find(final String eventType) {
        final String normalizedEventType = normalize(eventType);
        if (normalizedEventType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(specsByEventType.get(normalizedEventType));
    }

    private static void register(
            final Map<String, UiTaskProcessorSpec<KafkaUiTaskMessage>> mapped,
            final UiTaskProcessorSpec<KafkaUiTaskMessage> spec
    ) {
        final String eventType = normalize(spec.eventType());
        if (eventType == null) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (mapped.putIfAbsent(eventType, spec) != null) {
            throw new IllegalStateException("Duplicate UI task processor eventType: " + eventType);
        }
    }

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
}

