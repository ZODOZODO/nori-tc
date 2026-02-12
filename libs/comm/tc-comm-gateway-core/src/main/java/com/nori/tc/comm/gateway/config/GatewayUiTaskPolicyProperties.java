package com.nori.tc.comm.gateway.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UI Task 처리 정책 설정입니다.
 *
 * <p>다음 항목을 중앙에서 제어합니다.</p>
 * <p>- 이벤트별 처리 타임아웃(기본 30초)</p>
 * <p>- UI task 처리 재시도 정책</p>
 * <p>- UI reply 발행 재시도 정책</p>
 * <p>- traceId 중복 요청 스킵 정책(TTL)</p>
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.ui-task")
public class GatewayUiTaskPolicyProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskPolicyProperties.class);

    /**
     * EQP_CREATE 처리 타임아웃(ms)
     */
    private Long createTimeoutMs = 30_000L;

    /**
     * EQP_UPDATE 처리 타임아웃(ms)
     */
    private Long updateTimeoutMs = 30_000L;

    /**
     * EQP_DELETE 처리 타임아웃(ms)
     */
    private Long deleteTimeoutMs = 30_000L;

    /**
     * EQP_START 처리 타임아웃(ms)
     */
    private Long startTimeoutMs = 30_000L;

    /**
     * EQP_END 처리 타임아웃(ms)
     */
    private Long endTimeoutMs = 30_000L;

    /**
     * EQP_SEND_MESSAGE 처리 타임아웃(ms)
     */
    private Long sendMessageTimeoutMs = 30_000L;

    /**
     * EQP_UPDATE_JARFILE 처리 타임아웃(ms)
     */
    private Long updateJarfileTimeoutMs = 30_000L;

    /**
     * 처리 로직 재시도 최대 횟수
     */
    private Integer taskRetryMax = 1;

    /**
     * 처리 로직 재시도 backoff(ms)
     */
    private Long taskRetryBackoffMs = 200L;

    /**
     * UI reply 발행 재시도 최대 횟수
     */
    private Integer replyPublishRetryMax = 3;

    /**
     * UI reply 발행 재시도 backoff(ms)
     */
    private Long replyPublishRetryBackoffMs = 200L;

    /**
     * 동일 traceId 중복 판단 TTL(ms)
     */
    private Long duplicateTraceTtlMs = 600_000L;

    /**
     * 레코드 실패 후 동일 레코드 재시도 전 backoff(ms)
     *
     * <p>REP 발행 실패 등으로 커밋을 보류한 경우, 같은 레코드를 즉시 재소비하지 않도록 완충을 둡니다.</p>
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
                "GatewayUiTaskPolicyProperties validated. startTimeoutMs={}, endTimeoutMs={}, replyPublishRetryMax={}",
                startTimeoutMs,
                endTimeoutMs,
                replyPublishRetryMax
        );
    }

    /**
     * 양수 값 검증 유틸입니다.
     */
    private static void requirePositive(final String key, final Number value) {
        if (value == null || value.longValue() <= 0L) {
            throw new IllegalStateException(key + " must be > 0");
        }
    }

    /**
     * 0 이상 값 검증 유틸입니다.
     */
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
