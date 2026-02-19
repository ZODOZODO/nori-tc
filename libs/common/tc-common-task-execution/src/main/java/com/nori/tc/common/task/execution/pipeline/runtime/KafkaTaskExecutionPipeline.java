package com.nori.tc.common.task.execution.pipeline.runtime;

import com.nori.tc.common.kafka.processing.RetryDecision;
import com.nori.tc.common.kafka.processing.RetryPolicy;
import com.nori.tc.common.task.execution.pipeline.constants.KafkaTaskPipelineErrorKeys;
import com.nori.tc.common.task.execution.pipeline.constants.KafkaTaskPipelineReasonKeys;
import com.nori.tc.common.task.execution.pipeline.constants.KafkaTaskPipelineStage;
import com.nori.tc.common.task.execution.pipeline.exception.KafkaTaskReplyPublishException;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskDeduplicationStore;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskDlqReporter;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskMessageAccessor;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskProcessorRegistry;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskReplyPublisher;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskDispatchReport;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskProcessorSpec;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;

/**
 * Kafka 태스크를 공통 절차로 실행하는 파이프라인입니다.
 *
 * <p>처리 단계:</p>
 * <p>1) 요청 필드(eventType/traceId/eqpId) 추출 및 검증</p>
 * <p>2) 처리기 조회 및 중복(traceId) 검사</p>
 * <p>3) 처리기 실행(재시도 포함)</p>
 * <p>4) 응답 발행(재시도 포함)</p>
 * <p>5) 최종 실패 시 DLQ 보고</p>
 *
 * @param <T> 요청 타입
 */
public final class KafkaTaskExecutionPipeline<T> {

    private static final Logger log = LoggerFactory.getLogger(KafkaTaskExecutionPipeline.class);

    private final KafkaTaskMessageAccessor<T> accessor;
    private final KafkaTaskProcessorRegistry<T> registry;
    private final KafkaTaskReplyPublisher<T> replyPublisher;
    private final KafkaTaskDlqReporter<T> dlqReporter;
    private final KafkaTaskDeduplicationStore deduplicationStore;
    private final RetryPolicy taskRetryPolicy;
    private final RetryPolicy replyRetryPolicy;
    private final long duplicateTraceTtlMs;
    private final LongSupplier nowSupplier;

    /**
     * 파이프라인을 초기화합니다.
     *
     * @param accessor 요청 필드 접근자
     * @param registry 이벤트 타입별 처리기 레지스트리
     * @param replyPublisher 결과 응답 발행기
     * @param dlqReporter DLQ 보고기
     * @param deduplicationStore traceId 중복 저장소
     * @param taskRetryPolicy 처리 단계 재시도 정책
     * @param replyRetryPolicy 응답 발행 재시도 정책
     * @param duplicateTraceTtlMs traceId 보관 TTL(ms)
     * @param nowSupplier 현재 시각 공급자(epoch millis)
     */
    public KafkaTaskExecutionPipeline(
            final KafkaTaskMessageAccessor<T> accessor,
            final KafkaTaskProcessorRegistry<T> registry,
            final KafkaTaskReplyPublisher<T> replyPublisher,
            final KafkaTaskDlqReporter<T> dlqReporter,
            final KafkaTaskDeduplicationStore deduplicationStore,
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
     * 단일 요청을 처리하고 결과 리포트를 반환합니다.
     *
     * @param request 요청 메시지
     * @return 디스패치 결과
     */
    public KafkaTaskDispatchReport dispatch(final T request) {
        Objects.requireNonNull(request, "request is null");

        final String eqpId = requireText("eqpId", accessor.eqpId(request));
        final String traceId = requireText("traceId", accessor.traceId(request));
        final String normalizedEventType = normalizeEventType(accessor.eventType(request));
        final Optional<KafkaTaskProcessorSpec<T>> specOptional = normalizedEventType == null
                ? Optional.empty()
                : registry.find(normalizedEventType);
        final String replyEventType = resolveReplyEventType(normalizedEventType, specOptional);
        final long nowEpochMs = nowSupplier.getAsLong();

        if (deduplicationStore.isProcessed(traceId, nowEpochMs)) {
            log.info("Kafka task 중복(traceId)으로 처리 생략. eventType={}, eqpId={}, traceId={}",
                    normalizedEventType,
                    eqpId,
                    traceId);
            publishReplyWithRetry(request, replyEventType, KafkaTaskResult.pass(), normalizedEventType);
            return new KafkaTaskDispatchReport(KafkaTaskResult.pass(), replyEventType, true);
        }

        final KafkaTaskResult result;
        if (normalizedEventType == null) {
            dlqReporter.report(
                    request,
                    KafkaTaskPipelineStage.ROUTING,
                    KafkaTaskPipelineReasonKeys.ROUTING_FAILED,
                    "Unsupported eventType: null",
                    replyEventType
            );
            result = KafkaTaskResult.fail(
                    KafkaTaskPipelineErrorKeys.INVALID_EVENT_TYPE,
                    "Unsupported eventType: null"
            );
        } else if (specOptional.isEmpty()) {
            dlqReporter.report(
                    request,
                    KafkaTaskPipelineStage.ROUTING,
                    KafkaTaskPipelineReasonKeys.ROUTING_FAILED,
                    "Unsupported eventType: " + normalizedEventType,
                    replyEventType
            );
            result = KafkaTaskResult.fail(
                    KafkaTaskPipelineErrorKeys.HANDLER_NOT_FOUND,
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
        log.info("Kafka task dispatch 완료. eventType={}, eqpId={}, traceId={}, replyEventType={}, status={}, duplicateSkipped={}",
                normalizedEventType,
                eqpId,
                traceId,
                replyEventType,
                result.status(),
                false);
        return new KafkaTaskDispatchReport(result, replyEventType, false);
    }

    /**
     * 처리기 실행을 재시도 정책과 함께 수행합니다.
     */
    private KafkaTaskResult processWithRetry(
            final T request,
            final String eventType,
            final String eqpId,
            final String traceId,
            final KafkaTaskProcessorSpec<T> spec,
            final String replyEventType
    ) {
        int failedAttempt = 0;
        while (true) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Kafka task 처리 시작. eventType={}, eqpId={}, traceId={}, failedAttempt={}",
                            eventType, eqpId, traceId, failedAttempt);
                }

                final KafkaTaskResult result = spec.processor().process(request);
                if (log.isDebugEnabled()) {
                    log.debug("Kafka task 처리 성공. eventType={}, eqpId={}, traceId={}, status={}",
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
                        log.debug("Kafka task 재시도 예약. eventType={}, eqpId={}, traceId={}, nextFailedAttempt={}, backoffMs={}",
                                eventType, eqpId, traceId, failedAttempt + 1, retryDecision.backoffMs());
                    }
                    sleepBackoff(retryDecision.backoffMs());
                    continue;
                }

                dlqReporter.report(
                        request,
                        KafkaTaskPipelineStage.PROCESS,
                        KafkaTaskPipelineReasonKeys.PROCESS_FAILED,
                        "Kafka task handling failed after retries: " + ex.getMessage(),
                        replyEventType
                );
                log.error("Kafka task 처리 최종 실패. eventType={}, eqpId={}, traceId={}, failedAttempts={}",
                        eventType,
                        eqpId,
                        traceId,
                        failedAttempt,
                        ex);
                return KafkaTaskResult.fail(
                        KafkaTaskPipelineErrorKeys.INTERNAL_ERROR,
                        "Unhandled error while processing Kafka task"
                );
            }
        }
    }

    /**
     * 결과 발행을 재시도 정책과 함께 수행합니다.
     */
    private void publishReplyWithRetry(
            final T request,
            final String replyEventType,
            final KafkaTaskResult result,
            final String eventType
    ) {
        int failedAttempt = 0;
        while (true) {
            try {
                replyPublisher.publishResult(request, replyEventType, result);
                if (log.isDebugEnabled()) {
                    log.debug("Kafka task 응답 발행 성공. eventType={}, replyEventType={}, status={}",
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
                        log.debug("Kafka task 응답 재발행 예약. eventType={}, replyEventType={}, failedAttempt={}, backoffMs={}",
                                eventType, replyEventType, failedAttempt + 1, retryDecision.backoffMs());
                    }
                    sleepBackoff(retryDecision.backoffMs());
                    continue;
                }

                dlqReporter.report(
                        request,
                        KafkaTaskPipelineStage.PUBLISH,
                        KafkaTaskPipelineReasonKeys.PUBLISH_FAILED,
                        "Kafka task reply publish failed after retries: " + ex.getMessage(),
                        replyEventType
                );
                log.error("Kafka task 응답 발행 최종 실패. eventType={}, replyEventType={}, failedAttempts={}",
                        eventType,
                        replyEventType,
                        failedAttempt,
                        ex);
                throw new KafkaTaskReplyPublishException(
                        KafkaTaskPipelineErrorKeys.REPLY_PUBLISH_FAILED + ": failed to publish Kafka task reply",
                        ex
                );
            }
        }
    }

    /**
     * 처리기 사양 유무를 기준으로 응답 이벤트 타입을 계산합니다.
     */
    private static String resolveReplyEventType(
            final String eventType,
            final Optional<? extends KafkaTaskProcessorSpec<?>> specOptional
    ) {
        if (specOptional.isPresent()) {
            return specOptional.get().replyEventType();
        }
        if (eventType == null) {
            return "UNKNOWN_REP";
        }
        return eventType + "_REP";
    }

    /**
     * 이벤트 타입을 trim 후 대문자로 정규화합니다.
     */
    private static String normalizeEventType(final String eventType) {
        final String normalized = trimToNull(eventType);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase();
    }

    /**
     * 필수 텍스트 필드를 검증합니다.
     */
    private static String requireText(final String fieldName, final String value) {
        final String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    /**
     * 공백 문자열을 null로 정규화합니다.
     */
    private static String trimToNull(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * backoff 대기를 수행합니다.
     *
     * <p>비정상 장기 대기를 막기 위해 최대 60초 상한을 둡니다.</p>
     */
    private static void sleepBackoff(final long backoffMs) {
        if (backoffMs <= 0L) {
            return;
        }
        final long boundedBackoffMs = Math.min(backoffMs, 60_000L);
        final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(boundedBackoffMs);
        while (true) {
            final long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                return;
            }
            LockSupport.parkNanos(remainingNanos);
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Interrupted while waiting for retry backoff");
            }
        }
    }
}