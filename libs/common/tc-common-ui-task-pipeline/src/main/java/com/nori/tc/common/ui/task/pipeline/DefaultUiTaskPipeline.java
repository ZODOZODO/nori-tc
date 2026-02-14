package com.nori.tc.common.ui.task.pipeline;

import com.nori.tc.common.kafka.processing.RetryDecision;
import com.nori.tc.common.kafka.processing.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * UI 공통 이벤트 처리 파이프라인의 기본 구현입니다.
 *
 * <p>파이프라인 처리 순서:</p>
 * <p>1) 요청에서 eventType/traceId/eqpId를 추출하고 검증</p>
 * <p>2) eventType으로 처리기(Processor) 조회</p>
 * <p>3) traceId 중복 여부 확인</p>
 * <p>4) 처리기 실행 및 재시도</p>
 * <p>5) 응답 발행 및 재시도</p>
 * <p>6) 최종 실패를 DLQ 리포터로 보고</p>
 *
 * @param <T> 요청 메시지 타입
 */
public final class DefaultUiTaskPipeline<T> {

    private static final Logger log = LoggerFactory.getLogger(DefaultUiTaskPipeline.class);

    private final UiTaskMessageAccessor<T> accessor;
    private final UiTaskProcessorRegistry<T> registry;
    private final UiTaskReplyPublisher<T> replyPublisher;
    private final UiTaskDlqReporter<T> dlqReporter;
    private final UiTaskDeduplicationStore deduplicationStore;
    private final RetryPolicy taskRetryPolicy;
    private final RetryPolicy replyRetryPolicy;
    private final long duplicateTraceTtlMs;
    private final LongSupplier nowSupplier;

    /**
     * 기본 UI 파이프라인을 생성합니다.
     *
     * @param accessor 요청 필드 접근자
     * @param registry 이벤트 처리기 레지스트리
     * @param replyPublisher 응답 발행기
     * @param dlqReporter DLQ 리포터
     * @param deduplicationStore traceId 중복 저장소
     * @param taskRetryPolicy 처리 단계 재시도 정책
     * @param replyRetryPolicy 응답 발행 재시도 정책
     * @param duplicateTraceTtlMs 중복 기록 TTL(ms)
     * @param nowSupplier 현재 시각 공급자(epoch millis)
     */
    public DefaultUiTaskPipeline(
            final UiTaskMessageAccessor<T> accessor,
            final UiTaskProcessorRegistry<T> registry,
            final UiTaskReplyPublisher<T> replyPublisher,
            final UiTaskDlqReporter<T> dlqReporter,
            final UiTaskDeduplicationStore deduplicationStore,
            final RetryPolicy taskRetryPolicy,
            final RetryPolicy replyRetryPolicy,
            final long duplicateTraceTtlMs,
            final LongSupplier nowSupplier
    ) {
        this.accessor = Objects.requireNonNull(accessor, "accessor is null");
        this.registry = Objects.requireNonNull(registry, "registry is null");
        this.replyPublisher = Objects.requireNonNull(replyPublisher, "replyPublisher is null");
        this.dlqReporter = Objects.requireNonNull(dlqReporter, "dlqReporter is null");
        this.deduplicationStore = Objects.requireNonNull(deduplicationStore, "deduplicationStore is null");
        this.taskRetryPolicy = Objects.requireNonNull(taskRetryPolicy, "taskRetryPolicy is null");
        this.replyRetryPolicy = Objects.requireNonNull(replyRetryPolicy, "replyRetryPolicy is null");
        if (duplicateTraceTtlMs <= 0L) {
            throw new IllegalArgumentException("duplicateTraceTtlMs must be > 0");
        }
        this.duplicateTraceTtlMs = duplicateTraceTtlMs;
        this.nowSupplier = Objects.requireNonNull(nowSupplier, "nowSupplier is null");
    }

    /**
     * 단일 UI 요청을 공통 파이프라인으로 처리합니다.
     *
     * @param request 요청 원문
     * @return 처리 요약 리포트
     */
    public UiTaskDispatchReport dispatch(final T request) {
        Objects.requireNonNull(request, "request is null");

        final String eqpId = requireText("eqpId", accessor.eqpId(request));
        final String traceId = requireText("traceId", accessor.traceId(request));
        final String normalizedEventType = normalizeEventType(accessor.eventType(request));
        final Optional<UiTaskProcessorSpec<T>> specOptional = normalizedEventType == null
                ? Optional.empty()
                : registry.find(normalizedEventType);
        final String replyEventType = resolveReplyEventType(normalizedEventType, specOptional);
        final long nowEpochMs = nowSupplier.getAsLong();

        if (deduplicationStore.isProcessed(traceId, nowEpochMs)) {
            log.info("UI task skipped by duplicate traceId. eventType={}, eqpId={}, traceId={}",
                    normalizedEventType,
                    eqpId,
                    traceId);
            publishReplyWithRetry(request, replyEventType, UiTaskResult.pass(), normalizedEventType);
            return new UiTaskDispatchReport(UiTaskResult.pass(), replyEventType, true);
        }

        final UiTaskResult result;
        if (normalizedEventType == null) {
            dlqReporter.report(
                    request,
                    UiTaskPipelineStage.ROUTING,
                    UiTaskPipelineReasonCode.ROUTING_FAILED,
                    "Unsupported eventType: null",
                    replyEventType
            );
            result = UiTaskResult.fail(
                    UiTaskPipelineErrorCode.INVALID_EVENT_TYPE,
                    "Unsupported eventType: null"
            );
        } else if (specOptional.isEmpty()) {
            dlqReporter.report(
                    request,
                    UiTaskPipelineStage.ROUTING,
                    UiTaskPipelineReasonCode.ROUTING_FAILED,
                    "Unsupported eventType: " + normalizedEventType,
                    replyEventType
            );
            result = UiTaskResult.fail(
                    UiTaskPipelineErrorCode.HANDLER_NOT_FOUND,
                    "Unsupported eventType: " + normalizedEventType
            );
        } else {
            result = processWithRetry(
                    request,
                    normalizedEventType,
                    eqpId,
                    traceId,
                    specOptional.get(),
                    replyEventType
            );
        }

        publishReplyWithRetry(request, replyEventType, result, normalizedEventType);
        deduplicationStore.markProcessed(traceId, duplicateTraceTtlMs, nowEpochMs);
        log.info("UI task dispatch completed. eventType={}, eqpId={}, traceId={}, replyEventType={}, status={}, duplicateSkipped={}",
                normalizedEventType,
                eqpId,
                traceId,
                replyEventType,
                result.status(),
                false);
        return new UiTaskDispatchReport(result, replyEventType, false);
    }

    private UiTaskResult processWithRetry(
            final T request,
            final String eventType,
            final String eqpId,
            final String traceId,
            final UiTaskProcessorSpec<T> spec,
            final String replyEventType
    ) {
        int failedAttempt = 0;
        while (true) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("UI task dispatch start. eventType={}, eqpId={}, traceId={}, failedAttempt={}",
                            eventType, eqpId, traceId, failedAttempt);
                }
                final UiTaskResult result = spec.processor().process(request);
                if (log.isDebugEnabled()) {
                    log.debug("UI task handler execution succeeded. eventType={}, eqpId={}, traceId={}, status={}",
                            eventType,
                            eqpId,
                            traceId,
                            result.status());
                }
                return result;
            } catch (Exception ex) {
                failedAttempt++;
                final RetryDecision retryDecision = taskRetryPolicy.evaluate(failedAttempt, ex);
                if (retryDecision.shouldRetry()) {
                    if (log.isDebugEnabled()) {
                        log.debug("UI task retry scheduled. eventType={}, eqpId={}, traceId={}, nextFailedAttempt={}, backoffMs={}",
                                eventType, eqpId, traceId, failedAttempt + 1, retryDecision.backoffMs());
                    }
                    sleepBackoff(retryDecision.backoffMs());
                    continue;
                }

                dlqReporter.report(
                        request,
                        UiTaskPipelineStage.PROCESS,
                        UiTaskPipelineReasonCode.PROCESS_FAILED,
                        "UI task handling failed after retries: " + ex.getMessage(),
                        replyEventType
                );
                log.error("UI task handling failed after retries. eventType={}, eqpId={}, traceId={}, failedAttempts={}",
                        eventType,
                        eqpId,
                        traceId,
                        failedAttempt,
                        ex);
                return UiTaskResult.fail(
                        UiTaskPipelineErrorCode.INTERNAL_ERROR,
                        "Unhandled error while processing UI task"
                );
            }
        }
    }

    private void publishReplyWithRetry(
            final T request,
            final String replyEventType,
            final UiTaskResult result,
            final String eventType
    ) {
        int failedAttempt = 0;
        while (true) {
            try {
                replyPublisher.publishResult(request, replyEventType, result);
                if (log.isDebugEnabled()) {
                    log.debug("UI reply published. eventType={}, replyEventType={}, status={}",
                            eventType,
                            replyEventType,
                            result.status());
                }
                return;
            } catch (Exception ex) {
                failedAttempt++;
                final RetryDecision retryDecision = replyRetryPolicy.evaluate(failedAttempt, ex);
                if (retryDecision.shouldRetry()) {
                    if (log.isDebugEnabled()) {
                        log.debug("UI reply publish retry scheduled. eventType={}, replyEventType={}, failedAttempt={}, backoffMs={}",
                                eventType, replyEventType, failedAttempt + 1, retryDecision.backoffMs());
                    }
                    sleepBackoff(retryDecision.backoffMs());
                    continue;
                }

                dlqReporter.report(
                        request,
                        UiTaskPipelineStage.PUBLISH,
                        UiTaskPipelineReasonCode.PUBLISH_FAILED,
                        "UI reply publish failed after retries: " + ex.getMessage(),
                        replyEventType
                );
                log.error("UI reply publish failed after retries. eventType={}, replyEventType={}, failedAttempts={}",
                        eventType,
                        replyEventType,
                        failedAttempt,
                        ex);
                throw new UiTaskReplyPublishException(
                        UiTaskPipelineErrorCode.REPLY_PUBLISH_FAILED + ": failed to publish UI reply",
                        ex
                );
            }
        }
    }

    private static String resolveReplyEventType(
            final String eventType,
            final Optional<? extends UiTaskProcessorSpec<?>> specOptional
    ) {
        if (specOptional.isPresent()) {
            return specOptional.get().replyEventType();
        }
        if (eventType == null) {
            return "UNKNOWN_REP";
        }
        return eventType + "_REP";
    }

    private static String normalizeEventType(final String eventType) {
        final String normalized = trimToNull(eventType);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase();
    }

    private static String requireText(final String fieldName, final String value) {
        final String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private static String trimToNull(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void sleepBackoff(final long backoffMs) {
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
