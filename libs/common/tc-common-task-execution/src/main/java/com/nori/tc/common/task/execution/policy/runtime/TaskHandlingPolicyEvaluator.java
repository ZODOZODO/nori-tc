package com.nori.tc.common.task.execution.policy.runtime;

import com.nori.tc.common.consumer.runtime.RetryDecision;
import com.nori.tc.common.consumer.runtime.RetryPolicy;
import com.nori.tc.common.task.execution.policy.dlq.DlqRecordFactory;
import com.nori.tc.common.task.execution.policy.types.DlqRecord;
import com.nori.tc.common.task.execution.policy.types.TaskFailureCategory;
import com.nori.tc.common.task.execution.policy.types.TaskFailureContext;
import com.nori.tc.common.task.execution.policy.types.TaskHandlingDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 실패 처리 정책 평가기입니다.
 *
 * <p>평가 순서는 다음과 같습니다.</p>
 * <p>1) timeout 플래그가 true이면 실패 카테고리를 TIMEOUT으로 강제합니다.</p>
 * <p>2) WORKFLOW_NOT_FOUND는 비정상으로 보지 않고 CONTINUE로 처리합니다.</p>
 * <p>3) 나머지는 재시도 정책을 적용하고 소진 시 DLQ 또는 FAIL로 분기합니다.</p>
 */
public final class TaskHandlingPolicyEvaluator implements TaskHandlingPolicy {

    private static final Logger log = LoggerFactory.getLogger(TaskHandlingPolicyEvaluator.class);

    private final RetryPolicy retryPolicy;
    private final DlqRecordFactory dlqRecordFactory;
    private final boolean dlqEnabled;

    /**
     * 정책 평가기를 초기화합니다.
     *
     * @param retryPolicy 재시도 판단 정책
     * @param dlqRecordFactory DLQ 레코드 생성기
     * @param dlqEnabled DLQ 사용 여부
     */
    public TaskHandlingPolicyEvaluator(
            final RetryPolicy retryPolicy,
            final DlqRecordFactory dlqRecordFactory,
            final boolean dlqEnabled
    ) {
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy is null");
        this.dlqRecordFactory = Objects.requireNonNull(dlqRecordFactory, "dlqRecordFactory is null");
        this.dlqEnabled = dlqEnabled;
    }

    /**
     * 실패 컨텍스트를 평가해 다음 동작(RETRY/DLQ/CONTINUE/FAIL)을 반환합니다.
     *
     * @param context 실패 컨텍스트
     * @return 정책 결정 결과
     */
    @Override
    public TaskHandlingDecision decide(final TaskFailureContext context) {
        Objects.requireNonNull(context, "context is null");

        final TaskFailureCategory finalCategory = resolveFinalCategory(context);

        if (finalCategory == TaskFailureCategory.WORKFLOW_NOT_FOUND) {
            if (log.isDebugEnabled()) {
                log.debug("워크플로우 미매칭은 정상 continue로 처리합니다. topic={}, partition={}, offset={}, eqpId={}",
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
                log.info("재시도 소진으로 DLQ 결정을 반환합니다. topic={}, partition={}, offset={}, eqpId={}, attempt={}, category={}",
                        context.sourceTopic(),
                        context.sourcePartition(),
                        context.sourceOffset(),
                        context.eqpId(),
                        context.attempt(),
                        finalCategory);
            }
            return TaskHandlingDecision.dlq(finalCategory, dlqRecord);
        }

        log.info("DLQ 비활성 상태로 FAIL 결정을 반환합니다. topic={}, partition={}, offset={}, eqpId={}, attempt={}, category={}",
                context.sourceTopic(),
                context.sourcePartition(),
                context.sourceOffset(),
                context.eqpId(),
                context.attempt(),
                finalCategory);
        return TaskHandlingDecision.fail(finalCategory);
    }

    /**
     * timeout 트리거 여부를 반영해 최종 카테고리를 계산합니다.
     *
     * @param context 실패 컨텍스트
     * @return 최종 실패 카테고리
     */
    private static TaskFailureCategory resolveFinalCategory(final TaskFailureContext context) {
        if (context.timeoutTriggered()) {
            return TaskFailureCategory.TIMEOUT;
        }
        return context.failureCategory();
    }
}
