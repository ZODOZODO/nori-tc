package com.nori.tc.comm.gateway.config.props;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway UI Task 처리 정책 프로퍼티입니다.
 *
 * <p>prefix: {@code tc.comm.gateway.ui-task}</p>
 *
 * <p>이 프로퍼티는 다음 4개 범주를 함께 관리합니다.</p>
 * <p>1) UI 이벤트별 업무 처리 timeout</p>
 * <p>2) task/reply 재시도 정책</p>
 * <p>3) traceId 중복 차단 정책</p>
 * <p>4) UI task mailbox 런타임(큐/스레드/종료 대기) 정책</p>
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.ui-task")
public class GatewayUiTaskPolicyProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskPolicyProperties.class);

    /**
     * EQP_CREATE 처리 timeout(ms)
     */
    private Long createTimeoutMs;

    /**
     * EQP_UPDATE 처리 timeout(ms)
     */
    private Long updateTimeoutMs;

    /**
     * EQP_DELETE 처리 timeout(ms)
     */
    private Long deleteTimeoutMs;

    /**
     * EQP_START 처리 timeout(ms)
     */
    private Long startTimeoutMs;

    /**
     * EQP_END 처리 timeout(ms)
     */
    private Long endTimeoutMs;

    /**
     * EQP_SEND_MESSAGE 처리 timeout(ms)
     */
    private Long sendMessageTimeoutMs;

    /**
     * EQP_UPDATE_JARFILE 처리 timeout(ms)
     */
    private Long updateJarfileTimeoutMs;

    /**
     * UI task 처리 재시도 횟수
     */
    private Integer taskRetryMax;

    /**
     * UI task 처리 재시도 backoff(ms)
     */
    private Long taskRetryBackoffMs;

    /**
     * UI reply 발행 재시도 횟수
     */
    private Integer replyPublishRetryMax;

    /**
     * UI reply 발행 재시도 backoff(ms)
     */
    private Long replyPublishRetryBackoffMs;

    /**
     * traceId dedup 보존 시간(ms)
     */
    private Long duplicateTraceTtlMs;

    /**
     * traceId dedup 최대 저장 개수
     */
    private Integer duplicateTraceMaxSize;

    /**
     * Kafka 레코드 실패 재처리 backoff(ms)
     */
    private Long failedRecordRetryBackoffMs;

    /**
     * UI task mailbox 당 큐 용량
     */
    private Integer mailboxCapacity;

    /**
     * UI task mailbox dispatcher 스레드 수
     */
    private Integer dispatcherThreads;

    /**
     * UI task mailbox worker 스레드 수
     */
    private Integer workerThreads;

    /**
     * UI task mailbox 런타임 종료 대기 시간(ms)
     */
    private Long runtimeShutdownWaitMs;

    /**
     * 애플리케이션 기동 시 프로퍼티 값의 유효성을 검증합니다.
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
        requirePositive("tc.comm.gateway.ui-task.duplicate-trace-max-size", duplicateTraceMaxSize);
        requireNonNegative("tc.comm.gateway.ui-task.failed-record-retry-backoff-ms", failedRecordRetryBackoffMs);

        requirePositive("tc.comm.gateway.ui-task.mailbox-capacity", mailboxCapacity);
        requirePositive("tc.comm.gateway.ui-task.dispatcher-threads", dispatcherThreads);
        requirePositive("tc.comm.gateway.ui-task.worker-threads", workerThreads);
        requirePositive("tc.comm.gateway.ui-task.runtime-shutdown-wait-ms", runtimeShutdownWaitMs);

        log.info(
                "GatewayUiTaskPolicyProperties validated. mailboxCapacity={}, dispatcherThreads={}, workerThreads={}, runtimeShutdownWaitMs={}, taskRetryMax={}, replyPublishRetryMax={}",
                mailboxCapacity,
                dispatcherThreads,
                workerThreads,
                runtimeShutdownWaitMs,
                taskRetryMax,
                replyPublishRetryMax
        );
        if (log.isDebugEnabled()) {
            log.debug(
                    "Gateway UI task timeout policy. create={}, update={}, delete={}, start={}, end={}, sendMessage={}, updateJarfile={}",
                    createTimeoutMs,
                    updateTimeoutMs,
                    deleteTimeoutMs,
                    startTimeoutMs,
                    endTimeoutMs,
                    sendMessageTimeoutMs,
                    updateJarfileTimeoutMs
            );
            log.debug(
                    "Gateway UI task retry policy. taskBackoffMs={}, replyBackoffMs={}, duplicateTraceTtlMs={}, duplicateTraceMaxSize={}, failedRecordRetryBackoffMs={}",
                    taskRetryBackoffMs,
                    replyPublishRetryBackoffMs,
                    duplicateTraceTtlMs,
                    duplicateTraceMaxSize,
                    failedRecordRetryBackoffMs
            );
        }
    }

    /**
     * 양수 조건을 검증합니다.
     */
    private static void requirePositive(final String key, final Number value) {
        if (value == null || value.longValue() <= 0L) {
            throw new IllegalStateException(key + " must be > 0");
        }
    }

    /**
     * 0 이상 조건을 검증합니다.
     */
    private static void requireNonNegative(final String key, final Number value) {
        if (value == null || value.longValue() < 0L) {
            throw new IllegalStateException(key + " must be >= 0");
        }
    }

    /**
     * getCreateTimeoutMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getCreateTimeoutMs() {
        return createTimeoutMs;
    }

    /**
     * setCreateTimeoutMs 기능을 수행합니다.
     *
     * @param createTimeoutMs 입력 값
     */

    public void setCreateTimeoutMs(final long createTimeoutMs) {
        this.createTimeoutMs = createTimeoutMs;
    }

    /**
     * getUpdateTimeoutMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getUpdateTimeoutMs() {
        return updateTimeoutMs;
    }

    /**
     * setUpdateTimeoutMs 기능을 수행합니다.
     *
     * @param updateTimeoutMs 입력 값
     */

    public void setUpdateTimeoutMs(final long updateTimeoutMs) {
        this.updateTimeoutMs = updateTimeoutMs;
    }

    /**
     * getDeleteTimeoutMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getDeleteTimeoutMs() {
        return deleteTimeoutMs;
    }

    /**
     * setDeleteTimeoutMs 기능을 수행합니다.
     *
     * @param deleteTimeoutMs 입력 값
     */

    public void setDeleteTimeoutMs(final long deleteTimeoutMs) {
        this.deleteTimeoutMs = deleteTimeoutMs;
    }

    /**
     * getStartTimeoutMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getStartTimeoutMs() {
        return startTimeoutMs;
    }

    /**
     * setStartTimeoutMs 기능을 수행합니다.
     *
     * @param startTimeoutMs 입력 값
     */

    public void setStartTimeoutMs(final long startTimeoutMs) {
        this.startTimeoutMs = startTimeoutMs;
    }

    /**
     * getEndTimeoutMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getEndTimeoutMs() {
        return endTimeoutMs;
    }

    /**
     * setEndTimeoutMs 기능을 수행합니다.
     *
     * @param endTimeoutMs 입력 값
     */

    public void setEndTimeoutMs(final long endTimeoutMs) {
        this.endTimeoutMs = endTimeoutMs;
    }

    /**
     * getSendMessageTimeoutMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getSendMessageTimeoutMs() {
        return sendMessageTimeoutMs;
    }

    /**
     * setSendMessageTimeoutMs 기능을 수행합니다.
     *
     * @param sendMessageTimeoutMs 입력 값
     */

    public void setSendMessageTimeoutMs(final long sendMessageTimeoutMs) {
        this.sendMessageTimeoutMs = sendMessageTimeoutMs;
    }

    /**
     * getUpdateJarfileTimeoutMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getUpdateJarfileTimeoutMs() {
        return updateJarfileTimeoutMs;
    }

    /**
     * setUpdateJarfileTimeoutMs 기능을 수행합니다.
     *
     * @param updateJarfileTimeoutMs 입력 값
     */

    public void setUpdateJarfileTimeoutMs(final long updateJarfileTimeoutMs) {
        this.updateJarfileTimeoutMs = updateJarfileTimeoutMs;
    }

    /**
     * getTaskRetryMax 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public int getTaskRetryMax() {
        return taskRetryMax;
    }

    /**
     * setTaskRetryMax 기능을 수행합니다.
     *
     * @param taskRetryMax 입력 값
     */

    public void setTaskRetryMax(final int taskRetryMax) {
        this.taskRetryMax = taskRetryMax;
    }

    /**
     * getTaskRetryBackoffMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getTaskRetryBackoffMs() {
        return taskRetryBackoffMs;
    }

    /**
     * setTaskRetryBackoffMs 기능을 수행합니다.
     *
     * @param taskRetryBackoffMs 입력 값
     */

    public void setTaskRetryBackoffMs(final long taskRetryBackoffMs) {
        this.taskRetryBackoffMs = taskRetryBackoffMs;
    }

    /**
     * getReplyPublishRetryMax 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public int getReplyPublishRetryMax() {
        return replyPublishRetryMax;
    }

    /**
     * setReplyPublishRetryMax 기능을 수행합니다.
     *
     * @param replyPublishRetryMax 입력 값
     */

    public void setReplyPublishRetryMax(final int replyPublishRetryMax) {
        this.replyPublishRetryMax = replyPublishRetryMax;
    }

    /**
     * getReplyPublishRetryBackoffMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getReplyPublishRetryBackoffMs() {
        return replyPublishRetryBackoffMs;
    }

    /**
     * setReplyPublishRetryBackoffMs 기능을 수행합니다.
     *
     * @param replyPublishRetryBackoffMs 입력 값
     */

    public void setReplyPublishRetryBackoffMs(final long replyPublishRetryBackoffMs) {
        this.replyPublishRetryBackoffMs = replyPublishRetryBackoffMs;
    }

    /**
     * getDuplicateTraceTtlMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getDuplicateTraceTtlMs() {
        return duplicateTraceTtlMs;
    }

    /**
     * setDuplicateTraceTtlMs 기능을 수행합니다.
     *
     * @param duplicateTraceTtlMs 입력 값
     */

    public void setDuplicateTraceTtlMs(final long duplicateTraceTtlMs) {
        this.duplicateTraceTtlMs = duplicateTraceTtlMs;
    }

    /**
     * getDuplicateTraceMaxSize 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public int getDuplicateTraceMaxSize() {
        return duplicateTraceMaxSize;
    }

    /**
     * setDuplicateTraceMaxSize 기능을 수행합니다.
     *
     * @param duplicateTraceMaxSize 입력 값
     */

    public void setDuplicateTraceMaxSize(final int duplicateTraceMaxSize) {
        this.duplicateTraceMaxSize = duplicateTraceMaxSize;
    }

    /**
     * getFailedRecordRetryBackoffMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getFailedRecordRetryBackoffMs() {
        return failedRecordRetryBackoffMs;
    }

    /**
     * setFailedRecordRetryBackoffMs 기능을 수행합니다.
     *
     * @param failedRecordRetryBackoffMs 입력 값
     */

    public void setFailedRecordRetryBackoffMs(final long failedRecordRetryBackoffMs) {
        this.failedRecordRetryBackoffMs = failedRecordRetryBackoffMs;
    }

    /**
     * getMailboxCapacity 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public int getMailboxCapacity() {
        return mailboxCapacity;
    }

    /**
     * setMailboxCapacity 기능을 수행합니다.
     *
     * @param mailboxCapacity 입력 값
     */

    public void setMailboxCapacity(final int mailboxCapacity) {
        this.mailboxCapacity = mailboxCapacity;
    }

    /**
     * getDispatcherThreads 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public int getDispatcherThreads() {
        return dispatcherThreads;
    }

    /**
     * setDispatcherThreads 기능을 수행합니다.
     *
     * @param dispatcherThreads 입력 값
     */

    public void setDispatcherThreads(final int dispatcherThreads) {
        this.dispatcherThreads = dispatcherThreads;
    }

    /**
     * getWorkerThreads 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public int getWorkerThreads() {
        return workerThreads;
    }

    /**
     * setWorkerThreads 기능을 수행합니다.
     *
     * @param workerThreads 입력 값
     */

    public void setWorkerThreads(final int workerThreads) {
        this.workerThreads = workerThreads;
    }

    /**
     * getRuntimeShutdownWaitMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getRuntimeShutdownWaitMs() {
        return runtimeShutdownWaitMs;
    }

    /**
     * setRuntimeShutdownWaitMs 기능을 수행합니다.
     *
     * @param runtimeShutdownWaitMs 입력 값
     */

    public void setRuntimeShutdownWaitMs(final long runtimeShutdownWaitMs) {
        this.runtimeShutdownWaitMs = runtimeShutdownWaitMs;
    }
}
