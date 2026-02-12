package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.config.GatewayUiTaskPolicyProperties;
import com.nori.tc.comm.gateway.domain.dlq.DlqMessage;
import com.nori.tc.comm.gateway.domain.dlq.DlqReasonCode;
import com.nori.tc.messaging.kafka.starter.contract.KafkaMessageDispatcher;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * UI task dispatcher입니다.
 *
 * <p>주요 책임:
 * 1) eventType 파싱 및 processor 조회
 * 2) traceId 중복 처리
 * 3) task 처리/REP 발행 재시도
 * 4) 재시도 소진 시 DLQ 기록</p>
 */
@Component
public class GatewayUiTaskDispatcher implements KafkaMessageDispatcher<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskDispatcher.class);

    private final GatewayUiTaskProcessorRegistry processorRegistry;
    private final KafkaUiReplyPublisher replyPublisher;
    private final GatewayUiTaskPolicyProperties uiTaskPolicyProperties;
    private final GatewayUiTaskDlqPublisher dlqPublisher;
    private final UiTraceIdDeduplicationStore traceIdDeduplicationStore;

    /**
     * dispatcher 공통 의존성을 초기화합니다.
     */
    public GatewayUiTaskDispatcher(
            final GatewayUiTaskProcessorRegistry processorRegistry,
            final KafkaUiReplyPublisher replyPublisher,
            final GatewayUiTaskPolicyProperties uiTaskPolicyProperties,
            final GatewayUiTaskDlqPublisher dlqPublisher,
            final UiTraceIdDeduplicationStore traceIdDeduplicationStore
    ) {
        this.processorRegistry = Objects.requireNonNull(processorRegistry, "processorRegistry is null");
        this.replyPublisher = Objects.requireNonNull(replyPublisher, "replyPublisher is null");
        this.uiTaskPolicyProperties = Objects.requireNonNull(uiTaskPolicyProperties, "uiTaskPolicyProperties is null");
        this.dlqPublisher = Objects.requireNonNull(dlqPublisher, "dlqPublisher is null");
        this.traceIdDeduplicationStore = Objects.requireNonNull(
                traceIdDeduplicationStore,
                "traceIdDeduplicationStore is null"
        );
    }

    /**
     * UI task를 라우팅하고 REP 발행까지 수행합니다.
     *
     * <p>REP 발행이 성공해야만 정상 반환하며,
     * 발행 실패 시 예외를 발생시켜 상위 consumer가 commit 하지 않도록 합니다.</p>
     */
    @Override
    public void dispatch(final KafkaUiTaskMessage message) {
        Objects.requireNonNull(message, "message is null");

        final String traceId = message.metadata().traceId();
        final long nowEpochMs = System.currentTimeMillis();
        final KafkaUiTaskEventType eventType = resolveEventType(message);
        final Optional<GatewayUiTaskProcessorRegistry.GatewayUiTaskProcessorSpec> specOptional = processorRegistry.find(eventType);
        final String replyEventType = resolveReplyEventType(message, eventType, specOptional);

        // 동일 traceId 요청은 비즈니스 로직을 다시 수행하지 않고 PASS REP만 발행합니다.
        if (traceIdDeduplicationStore.isProcessed(traceId, nowEpochMs)) {
            log.info("UI task skipped by duplicate traceId. eventType={}, eqpId={}, traceId={}",
                    message.metadata().eventType(),
                    message.data().eqpId(),
                    traceId);
            publishReplyWithRetry(message, replyEventType, GatewayUiTaskResult.pass());
            return;
        }

        final GatewayUiTaskResult result;
        if (eventType == null || specOptional.isEmpty()) {
            dlqPublisher.publish(
                    message,
                    DlqMessage.STAGE_ROUTING,
                    DlqReasonCode.ROUTING_FAILED,
                    buildUnsupportedEventMessage(message),
                    replyEventType
            );
            result = GatewayUiTaskResult.fail(
                    eventType == null
                            ? GatewayUiTaskErrorCode.INVALID_EVENT_TYPE
                            : GatewayUiTaskErrorCode.HANDLER_NOT_FOUND,
                    buildUnsupportedEventMessage(message)
            );
        } else {
            result = handleWithRetry(specOptional.get(), message, replyEventType);
        }

        publishReplyWithRetry(message, replyEventType, result);
        traceIdDeduplicationStore.markProcessed(
                traceId,
                uiTaskPolicyProperties.getDuplicateTraceTtlMs(),
                nowEpochMs
        );
    }

    /**
     * eventType 문자열을 enum으로 변환합니다.
     */
    private KafkaUiTaskEventType resolveEventType(final KafkaUiTaskMessage message) {
        try {
            return KafkaUiTaskEventType.fromText(message.metadata().eventType());
        } catch (Exception ex) {
            log.warn("UI task has unsupported eventType. eventType={}, eqpId={}, traceId={}",
                    message.metadata().eventType(),
                    message.data().eqpId(),
                    message.metadata().traceId());
            return null;
        }
    }

    /**
     * 응답 eventType을 결정합니다.
     */
    private String resolveReplyEventType(
            final KafkaUiTaskMessage message,
            final KafkaUiTaskEventType eventType,
            final Optional<GatewayUiTaskProcessorRegistry.GatewayUiTaskProcessorSpec> specOptional
    ) {
        if (specOptional.isPresent()) {
            return specOptional.get().replyEventType();
        }
        if (eventType != null) {
            return eventType.name() + "_REP";
        }
        final String raw = message.metadata().eventType();
        if (raw == null || raw.isBlank()) {
            return "UNKNOWN_REP";
        }
        return raw.trim().toUpperCase() + "_REP";
    }

    /**
     * UI task 실행을 재시도 정책에 따라 수행합니다.
     */
    private GatewayUiTaskResult handleWithRetry(
            final GatewayUiTaskProcessorRegistry.GatewayUiTaskProcessorSpec spec,
            final KafkaUiTaskMessage message,
            final String replyEventType
    ) {
        int attempt = 0;
        while (true) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("UI task dispatch start. eventType={}, eqpId={}, traceId={}, attempt={}",
                            spec.eventType(),
                            message.data().eqpId(),
                            message.metadata().traceId(),
                            attempt);
                }
                return spec.processor().process(message);
            } catch (Exception ex) {
                if (attempt >= uiTaskPolicyProperties.getTaskRetryMax()) {
                    dlqPublisher.publish(
                            message,
                            DlqMessage.STAGE_ROUTING,
                            DlqReasonCode.ROUTING_FAILED,
                            "UI task handling failed after retries: " + ex.getMessage(),
                            replyEventType
                    );
                    log.error("UI task handling failed after retries. eventType={}, eqpId={}, traceId={}, attempts={}",
                            spec.eventType(),
                            message.data().eqpId(),
                            message.metadata().traceId(),
                            attempt + 1,
                            ex);
                    return GatewayUiTaskResult.fail(
                            GatewayUiTaskErrorCode.INTERNAL_ERROR,
                            "Unhandled error while processing UI task"
                    );
                }

                attempt++;
                log.warn("UI task handling retry. eventType={}, eqpId={}, traceId={}, nextAttempt={}",
                        spec.eventType(),
                        message.data().eqpId(),
                        message.metadata().traceId(),
                        attempt);
                sleepQuietly(uiTaskPolicyProperties.getTaskRetryBackoffMs());
            }
        }
    }

    /**
     * UI reply 발행을 재시도 정책으로 수행합니다.
     *
     * <p>최종 실패 시 예외를 던져 상위 consumer가 commit 하지 않도록 합니다.</p>
     */
    private void publishReplyWithRetry(
            final KafkaUiTaskMessage message,
            final String replyEventType,
            final GatewayUiTaskResult result
    ) {
        int attempt = 0;
        while (true) {
            try {
                replyPublisher.publishResult(message, replyEventType, result);
                return;
            } catch (Exception ex) {
                if (attempt >= uiTaskPolicyProperties.getReplyPublishRetryMax()) {
                    dlqPublisher.publish(
                            message,
                            DlqMessage.STAGE_PUBLISH,
                            DlqReasonCode.PUBLISH_FAILED,
                            "UI reply publish failed after retries: " + ex.getMessage(),
                            replyEventType
                    );
                    log.error("UI reply publish failed after retries. eventType={}, replyEventType={}, eqpId={}, traceId={}",
                            message.metadata().eventType(),
                            replyEventType,
                            message.data().eqpId(),
                            message.metadata().traceId(),
                            ex);
                    throw new IllegalStateException(
                            GatewayUiTaskErrorCode.REPLY_PUBLISH_FAILED + ": failed to publish UI reply",
                            ex
                    );
                }

                attempt++;
                log.warn("UI reply publish retry. eventType={}, replyEventType={}, eqpId={}, traceId={}, nextAttempt={}",
                        message.metadata().eventType(),
                        replyEventType,
                        message.data().eqpId(),
                        message.metadata().traceId(),
                        attempt);
                sleepQuietly(uiTaskPolicyProperties.getReplyPublishRetryBackoffMs());
            }
        }
    }

    /**
     * 지원하지 않는 eventType 오류 메시지를 생성합니다.
     */
    private String buildUnsupportedEventMessage(final KafkaUiTaskMessage message) {
        final String rawEventType = message.metadata() == null ? null : message.metadata().eventType();
        return "Unsupported eventType: " + (rawEventType == null ? "null" : rawEventType);
    }

    /**
     * 재시도 backoff 대기 유틸입니다.
     */
    private void sleepQuietly(final long backoffMs) {
        if (backoffMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for retry backoff", ex);
        }
    }
}
