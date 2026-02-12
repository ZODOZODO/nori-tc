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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * UI task dispatcher입니다.
 *
 * <p>주요 책임:</p>
 * <p>- 이벤트 타입별 핸들러 라우팅</p>
 * <p>- 공통 REP 발행(PASS/FAIL)</p>
 * <p>- traceId 중복 스킵 처리</p>
 * <p>- 처리/REP 발행 재시도</p>
 * <p>- 재시도 소진 시 DLQ 기록</p>
 */
@Component
public class GatewayUiTaskDispatcher implements KafkaMessageDispatcher<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskDispatcher.class);

    private final Map<KafkaUiTaskEventType, GatewayUiTaskHandler> handlersByType;
    private final KafkaUiReplyPublisher replyPublisher;
    private final GatewayUiTaskPolicyProperties uiTaskPolicyProperties;
    private final GatewayUiTaskDlqPublisher dlqPublisher;
    private final UiTraceIdDeduplicationStore traceIdDeduplicationStore;

    /**
     * 핸들러 맵과 공통 부가 컴포넌트를 초기화합니다.
     */
    public GatewayUiTaskDispatcher(
            final List<GatewayUiTaskHandler> handlers,
            final KafkaUiReplyPublisher replyPublisher,
            final GatewayUiTaskPolicyProperties uiTaskPolicyProperties,
            final GatewayUiTaskDlqPublisher dlqPublisher,
            final UiTraceIdDeduplicationStore traceIdDeduplicationStore
    ) {
        Objects.requireNonNull(handlers, "handlers is null");
        this.replyPublisher = Objects.requireNonNull(replyPublisher, "replyPublisher is null");
        this.uiTaskPolicyProperties = Objects.requireNonNull(uiTaskPolicyProperties, "uiTaskPolicyProperties is null");
        this.dlqPublisher = Objects.requireNonNull(dlqPublisher, "dlqPublisher is null");
        this.traceIdDeduplicationStore = Objects.requireNonNull(
                traceIdDeduplicationStore,
                "traceIdDeduplicationStore is null"
        );

        final Map<KafkaUiTaskEventType, GatewayUiTaskHandler> mapped = new EnumMap<>(KafkaUiTaskEventType.class);
        for (GatewayUiTaskHandler handler : handlers) {
            final GatewayUiTaskHandler previous = mapped.put(handler.eventType(), handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate UI task handler for eventType=" + handler.eventType());
            }
        }
        this.handlersByType = mapped;

        log.info("UI task handlers initialized. count={}, eventTypes={}", handlersByType.size(), handlersByType.keySet());
    }

    /**
     * UI task를 라우팅하고 REP 발행까지 수행합니다.
     *
     * <p>REP 발행에 성공해야만 정상 반환하며,
     * 실패 시 예외를 발생시켜 상위 consumer가 커밋하지 않도록 합니다.</p>
     */
    @Override
    public void dispatch(final KafkaUiTaskMessage message) {
        Objects.requireNonNull(message, "message is null");

        final String traceId = message.metadata().traceId();
        final long nowEpochMs = System.currentTimeMillis();
        final KafkaUiTaskEventType eventType = resolveEventType(message);
        final GatewayUiTaskHandler handler = resolveHandler(eventType);
        final String replyEventType = resolveReplyEventType(message, eventType, handler);

        // 동일 traceId 중복 요청은 비즈니스 로직을 스킵하고 PASS REP만 회신합니다.
        if (traceIdDeduplicationStore.isProcessed(traceId, nowEpochMs)) {
            log.info("UI task skipped by duplicate traceId. eventType={}, eqpId={}, traceId={}",
                    message.metadata().eventType(),
                    message.data().eqpId(),
                    traceId);
            publishReplyWithRetry(message, replyEventType, GatewayUiTaskResult.pass());
            return;
        }

        final GatewayUiTaskResult result;
        if (handler == null || eventType == null) {
            dlqPublisher.publish(
                    message,
                    DlqMessage.STAGE_ROUTING,
                    DlqReasonCode.ROUTING_FAILED,
                    buildUnsupportedEventMessage(message),
                    replyEventType
            );
            result = GatewayUiTaskResult.fail(
                    handler == null
                            ? GatewayUiTaskErrorCode.HANDLER_NOT_FOUND
                            : GatewayUiTaskErrorCode.INVALID_EVENT_TYPE,
                    buildUnsupportedEventMessage(message)
            );
        } else {
            result = handleWithRetry(handler, message, replyEventType);
        }

        publishReplyWithRetry(message, replyEventType, result);
        traceIdDeduplicationStore.markProcessed(
                traceId,
                uiTaskPolicyProperties.getDuplicateTraceTtlMs(),
                nowEpochMs
        );
    }

    /**
     * 이벤트 타입 문자열을 enum으로 변환합니다.
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
     * 이벤트 타입으로 핸들러를 조회합니다.
     */
    private GatewayUiTaskHandler resolveHandler(final KafkaUiTaskEventType eventType) {
        if (eventType == null) {
            return null;
        }
        final GatewayUiTaskHandler handler = handlersByType.get(eventType);
        if (handler == null) {
            log.warn("UI task has no handler. eventType={}", eventType);
        }
        return handler;
    }

    /**
     * 응답 이벤트 타입을 결정합니다.
     *
     * <p>핸들러가 있으면 핸들러 정의값을 우선합니다.
     * 핸들러가 없거나 eventType 파싱 실패 시에는 원문 eventType + "_REP" 규칙으로 보정합니다.</p>
     */
    private String resolveReplyEventType(
            final KafkaUiTaskMessage message,
            final KafkaUiTaskEventType eventType,
            final GatewayUiTaskHandler handler
    ) {
        if (handler != null) {
            return handler.replyEventType();
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
     * 핸들러 로직을 재시도 정책에 따라 실행합니다.
     */
    private GatewayUiTaskResult handleWithRetry(
            final GatewayUiTaskHandler handler,
            final KafkaUiTaskMessage message,
            final String replyEventType
    ) {
        int attempt = 0;
        while (true) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("UI task dispatch start. eventType={}, handler={}, eqpId={}, traceId={}, attempt={}",
                            handler.eventType(),
                            handler.getClass().getSimpleName(),
                            message.data().eqpId(),
                            message.metadata().traceId(),
                            attempt);
                }

                final GatewayUiTaskResult result = handler.handle(message);
                if (result == null) {
                    return GatewayUiTaskResult.fail(
                            GatewayUiTaskErrorCode.INTERNAL_ERROR,
                            "Handler returned null result"
                    );
                }
                return result;
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
                            handler.eventType(),
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
                        handler.eventType(),
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
     * <p>최종 실패 시 예외를 던져 상위 consumer가 커밋하지 않도록 합니다.</p>
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
