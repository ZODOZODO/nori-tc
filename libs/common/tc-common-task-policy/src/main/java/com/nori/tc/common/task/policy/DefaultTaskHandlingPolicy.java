package com.nori.tc.common.task.policy;

import com.nori.tc.common.kafka.processing.RetryDecision;
import com.nori.tc.common.kafka.processing.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 기본 실패 처리 정책 구현입니다.
 *
 * <p>정책 규칙은 다음과 같습니다.</p>
 * <p>1) timeoutTriggered=true 이면 최종 카테고리를 TIMEOUT으로 강제</p>
 * <p>2) WORKFLOW_NOT_FOUND는 실패가 아닌 CONTINUE로 처리</p>
 * <p>3) 그 외 카테고리는 retryPolicy를 평가하여 RETRY 또는 DLQ를 결정</p>
 */
public final class DefaultTaskHandlingPolicy implements TaskHandlingPolicy {

    private static final Logger log = LoggerFactory.getLogger(DefaultTaskHandlingPolicy.class);

    private final RetryPolicy retryPolicy;
    private final DlqRecordFactory dlqRecordFactory;
    private final boolean dlqEnabled;

    /**
     * 기본 정책을 생성합니다.
     *
     * @param retryPolicy retry 평가 정책
     * @param dlqRecordFactory DLQ 레코드 생성 팩토리
     * @param dlqEnabled DLQ 기능 활성화 여부
     */
    public DefaultTaskHandlingPolicy(
            final RetryPolicy retryPolicy,
            final DlqRecordFactory dlqRecordFactory,
            final boolean dlqEnabled
    ) {
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy is null");
        this.dlqRecordFactory = Objects.requireNonNull(dlqRecordFactory, "dlqRecordFactory is null");
        this.dlqEnabled = dlqEnabled;
    }

    /**
     * 실패 컨텍스트를 평가하여 RETRY/DLQ/CONTINUE/FAIL을 결정합니다.
     *
     * @param context 실패 컨텍스트
     * @return 정책 평가 결과
     */
    @Override
    public TaskHandlingDecision decide(final TaskFailureContext context) {
        Objects.requireNonNull(context, "context is null");

        final TaskFailureCategory finalCategory = resolveFinalCategory(context);

        if (finalCategory == TaskFailureCategory.WORKFLOW_NOT_FOUND) {
            if (log.isDebugEnabled()) {
                log.debug("워크플로우 미매칭은 정상 continue 처리합니다. topic={}, partition={}, offset={}, eqpId={}",
                        context.sourceTopic(),
                        context.sourcePartition(),
                        context.sourceOffset(),
                        context.eqpId());
            }
            return TaskHandlingDecision.continueNormally(finalCategory);
        }

        final Throwable failure = context.failure() == null
                ? new IllegalStateException("Unknown failure")
                : context.failure();
        final RetryDecision retryDecision = retryPolicy.evaluate(context.attempt(), failure);

        if (retryDecision.shouldRetry()) {
            if (log.isDebugEnabled()) {
                log.debug("재시도 결정을 반환합니다. topic={}, partition={}, offset={}, eqpId={}, attempt={}, category={}, backoffMs={}",
                        context.sourceTopic(),
                        context.sourcePartition(),
                        context.sourceOffset(),
                        context.eqpId(),
                        context.attempt(),
                        finalCategory,
                        retryDecision.backoffMs());
            }
            return TaskHandlingDecision.retry(finalCategory, retryDecision.backoffMs());
        }

        if (dlqEnabled) {
            final DlqRecord dlqRecord = dlqRecordFactory.create(context, finalCategory);
            if (log.isInfoEnabled()) {
                log.info("재시도 한도 초과로 DLQ 결정을 반환합니다. topic={}, partition={}, offset={}, eqpId={}, attempt={}, category={}",
                        context.sourceTopic(),
                        context.sourcePartition(),
                        context.sourceOffset(),
                        context.eqpId(),
                        context.attempt(),
                        finalCategory);
            }
            return TaskHandlingDecision.dlq(finalCategory, dlqRecord);
        }

        log.info("DLQ 비활성 상태라 FAIL 결정을 반환합니다. topic={}, partition={}, offset={}, eqpId={}, attempt={}, category={}",
                context.sourceTopic(),
                context.sourcePartition(),
                context.sourceOffset(),
                context.eqpId(),
                context.attempt(),
                finalCategory);
        return TaskHandlingDecision.fail(finalCategory);
    }

    private static TaskFailureCategory resolveFinalCategory(final TaskFailureContext context) {
        if (context.timeoutTriggered()) {
            return TaskFailureCategory.TIMEOUT;
        }
        return context.failureCategory();
    }
}
