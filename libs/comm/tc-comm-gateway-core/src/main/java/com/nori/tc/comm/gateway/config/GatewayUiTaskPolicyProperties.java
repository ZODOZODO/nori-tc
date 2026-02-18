package com.nori.tc.comm.gateway.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway UI Task 처리 정책 속성입니다.
 *
 * <p>prefix: {@code tc.comm.gateway.ui-task}</p>
 *
 * <p>본 속성은 UI 명령 처리의 타임아웃/재시도/중복 방지/라이프사이클 동기대기 모드를 통합 제어합니다.</p>
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.ui-task")
public class GatewayUiTaskPolicyProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskPolicyProperties.class);

    /** EQP_CREATE 처리 타임아웃(ms) */
    private Long createTimeoutMs = 30_000L;

    /** EQP_UPDATE 처리 타임아웃(ms) */
    private Long updateTimeoutMs = 30_000L;

    /** EQP_DELETE 처리 타임아웃(ms) */
    private Long deleteTimeoutMs = 30_000L;

    /** EQP_START 처리 타임아웃(ms) */
    private Long startTimeoutMs = 30_000L;

    /** EQP_END 처리 타임아웃(ms) */
    private Long endTimeoutMs = 30_000L;

    /** EQP_SEND_MESSAGE 처리 타임아웃(ms) */
    private Long sendMessageTimeoutMs = 30_000L;

    /** EQP_UPDATE_JARFILE 처리 타임아웃(ms) */
    private Long updateJarfileTimeoutMs = 30_000L;

    /**
     * UI lifecycle(START/END)에서 실제 연결/해제 완료까지 동기 대기할지 여부입니다.
     *
     * <p>Phase 2 기본값은 false(비동기 상태머신)이며, true로 설정하면 기존 동기 대기 경로를 사용합니다.</p>
     */
    private boolean lifecycleSyncWaitEnabled = false;

    /** UI task 처리 로직 최대 재시도 횟수 */
    private Integer taskRetryMax = 1;

    /** UI task 처리 로직 재시도 backoff(ms) */
    private Long taskRetryBackoffMs = 200L;

    /** UI reply 발행 최대 재시도 횟수 */
    private Integer replyPublishRetryMax = 3;

    /** UI reply 발행 재시도 backoff(ms) */
    private Long replyPublishRetryBackoffMs = 200L;

    /** 동일 traceId 중복 요청 차단 TTL(ms) */
    private Long duplicateTraceTtlMs = 600_000L;

    /**
     * 레코드 처리 실패 후 동일 레코드 재시도 전 backoff(ms)입니다.
     *
     * <p>REP 발행 실패 등으로 커밋이 보류될 때, 같은 레코드를 즉시 재처리하지 않도록 완충 역할을 합니다.</p>
     */
    private Long failedRecordRetryBackoffMs = 500L;

    /**
     * 모든 정책값의 유효성을 검증합니다.
     */
    @PostConstruct
    public void validate() {
        requirePositive("tc.comm.gateway.ui-task.create-timeout-ms", createTimeoutMs);
        requirePositive("tc.comm.gateway.ui-task.update-timeout-ms", updateTimeoutMs);
        requirePositive("tc.comm.gateway.ui-task.delete-timeout-ms", deleteTimeoutMs);
        requirePositive("tc.comm.gateway.ui-task.start-timeout-ms", startTimeoutMs);
        requirePositive("tc.comm.gateway.ui-task.end-timeout-ms", endTimeoutMs);
        requirePositive("tc.comm.gateway.ui-task.send-message-timeout-ms", sendMessageTimeoutMs);
        requirePositive("tc.comm.gateway.ui-task.update-jarfile-timeout-ms", updateJarfileTimeoutMs);

        requireNonNegative("tc.comm.gateway.ui-task.task-retry-max", taskRetryMax);
        requireNonNegative("tc.comm.gateway.ui-task.task-retry-backoff-ms", taskRetryBackoffMs);
        requireNonNegative("tc.comm.gateway.ui-task.reply-publish-retry-max", replyPublishRetryMax);
        requireNonNegative("tc.comm.gateway.ui-task.reply-publish-retry-backoff-ms", replyPublishRetryBackoffMs);
        requirePositive("tc.comm.gateway.ui-task.duplicate-trace-ttl-ms", duplicateTraceTtlMs);
        requireNonNegative("tc.comm.gateway.ui-task.failed-record-retry-backoff-ms", failedRecordRetryBackoffMs);

        log.info(
                "GatewayUiTaskPolicyProperties validated. startTimeoutMs={}, endTimeoutMs={}, replyPublishRetryMax={}, lifecycleSyncWaitEnabled={}",
                startTimeoutMs,
                endTimeoutMs,
                replyPublishRetryMax,
                lifecycleSyncWaitEnabled
        );
    }

    /** 양수 검증 유틸입니다. */
    private static void requirePositive(final String key, final Number value) {
        if (value == null || value.longValue() <= 0L) {
            throw new IllegalStateException(key + " must be > 0");
        }
    }

    /** 0 이상 검증 유틸입니다. */
    private static void requireNonNegative(final String key, final Number value) {
        if (value == null || value.longValue() < 0L) {
            throw new IllegalStateException(key + " must be >= 0");
        }
    }

    public long getCreateTimeoutMs() {
        return createTimeoutMs;
    }

    public void setCreateTimeoutMs(final long createTimeoutMs) {
        this.createTimeoutMs = createTimeoutMs;
    }

    public long getUpdateTimeoutMs() {
        return updateTimeoutMs;
    }

    public void setUpdateTimeoutMs(final long updateTimeoutMs) {
        this.updateTimeoutMs = updateTimeoutMs;
    }

    public long getDeleteTimeoutMs() {
        return deleteTimeoutMs;
    }

    public void setDeleteTimeoutMs(final long deleteTimeoutMs) {
        this.deleteTimeoutMs = deleteTimeoutMs;
    }

    public long getStartTimeoutMs() {
        return startTimeoutMs;
    }

    public void setStartTimeoutMs(final long startTimeoutMs) {
        this.startTimeoutMs = startTimeoutMs;
    }

    public long getEndTimeoutMs() {
        return endTimeoutMs;
    }

    public void setEndTimeoutMs(final long endTimeoutMs) {
        this.endTimeoutMs = endTimeoutMs;
    }

    public long getSendMessageTimeoutMs() {
        return sendMessageTimeoutMs;
    }

    public void setSendMessageTimeoutMs(final long sendMessageTimeoutMs) {
        this.sendMessageTimeoutMs = sendMessageTimeoutMs;
    }

    public long getUpdateJarfileTimeoutMs() {
        return updateJarfileTimeoutMs;
    }

    public void setUpdateJarfileTimeoutMs(final long updateJarfileTimeoutMs) {
        this.updateJarfileTimeoutMs = updateJarfileTimeoutMs;
    }

    public boolean isLifecycleSyncWaitEnabled() {
        return lifecycleSyncWaitEnabled;
    }

    public void setLifecycleSyncWaitEnabled(final boolean lifecycleSyncWaitEnabled) {
        this.lifecycleSyncWaitEnabled = lifecycleSyncWaitEnabled;
    }

    public int getTaskRetryMax() {
        return taskRetryMax;
    }

    public void setTaskRetryMax(final int taskRetryMax) {
        this.taskRetryMax = taskRetryMax;
    }

    public long getTaskRetryBackoffMs() {
        return taskRetryBackoffMs;
    }

    public void setTaskRetryBackoffMs(final long taskRetryBackoffMs) {
        this.taskRetryBackoffMs = taskRetryBackoffMs;
    }

    public int getReplyPublishRetryMax() {
        return replyPublishRetryMax;
    }

    public void setReplyPublishRetryMax(final int replyPublishRetryMax) {
        this.replyPublishRetryMax = replyPublishRetryMax;
    }

    public long getReplyPublishRetryBackoffMs() {
        return replyPublishRetryBackoffMs;
    }

    public void setReplyPublishRetryBackoffMs(final long replyPublishRetryBackoffMs) {
        this.replyPublishRetryBackoffMs = replyPublishRetryBackoffMs;
    }

    public long getDuplicateTraceTtlMs() {
        return duplicateTraceTtlMs;
    }

    public void setDuplicateTraceTtlMs(final long duplicateTraceTtlMs) {
        this.duplicateTraceTtlMs = duplicateTraceTtlMs;
    }

    public long getFailedRecordRetryBackoffMs() {
        return failedRecordRetryBackoffMs;
    }

    public void setFailedRecordRetryBackoffMs(final long failedRecordRetryBackoffMs) {
        this.failedRecordRetryBackoffMs = failedRecordRetryBackoffMs;
    }
}
