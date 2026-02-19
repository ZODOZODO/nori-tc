package com.nori.tc.business.adapters.kafka.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UI task 파이프라인 정책 프로퍼티입니다.
 *
 * <p>prefix: {@code tc.business.core.ui-task}</p>
 */
@ConfigurationProperties(prefix = "tc.business.core.ui-task")
public class BusinessUiTaskPolicyProperties {

    private static final Logger log = LoggerFactory.getLogger(BusinessUiTaskPolicyProperties.class);

    /**
     * UI REP metadata.source 값입니다.
     */
    private String source;

    /**
     * traceId 중복 처리 TTL(ms)입니다.
     */
    private Long duplicateTraceTtlMs;

    /**
     * traceId 중복 처리 캐시 최대 엔트리 수입니다.
     */
    private Integer duplicateTraceMaxSize;

    /**
     * UI task 처리 재시도 최대 시도 횟수입니다.
     */
    private Integer taskRetryMaxAttempts;

    /**
     * UI task 처리 재시도 backoff(ms)입니다.
     */
    private Long taskRetryBackoffMs;

    /**
     * UI REP 발행 재시도 최대 시도 횟수입니다.
     */
    private Integer replyRetryMaxAttempts;

    /**
     * UI REP 발행 재시도 backoff(ms)입니다.
     */
    private Long replyRetryBackoffMs;

    /**
     * 설정값 유효성을 검증합니다.
     */
    @PostConstruct
    public void validate() {
        requireText("tc.business.core.ui-task.source", source);
        requirePositive("tc.business.core.ui-task.duplicate-trace-ttl-ms", duplicateTraceTtlMs);
        requirePositive("tc.business.core.ui-task.duplicate-trace-max-size", duplicateTraceMaxSize);
        requirePositive("tc.business.core.ui-task.task-retry-max-attempts", taskRetryMaxAttempts);
        requireNonNegative("tc.business.core.ui-task.task-retry-backoff-ms", taskRetryBackoffMs);
        requirePositive("tc.business.core.ui-task.reply-retry-max-attempts", replyRetryMaxAttempts);
        requireNonNegative("tc.business.core.ui-task.reply-retry-backoff-ms", replyRetryBackoffMs);

        log.info("BusinessUiTaskPolicyProperties validated. source={}, duplicateTraceTtlMs={}, duplicateTraceMaxSize={}, taskRetryMaxAttempts={}, replyRetryMaxAttempts={}",
                source,
                duplicateTraceTtlMs,
                duplicateTraceMaxSize,
                taskRetryMaxAttempts,
                replyRetryMaxAttempts);
    }

    /**
     * getSource 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public String getSource() {
        return source;
    }

    /**
     * setSource 기능을 수행합니다.
     *
     * @param source 입력 값
     */

    public void setSource(final String source) {
        this.source = source;
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
     * getTaskRetryMaxAttempts 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public int getTaskRetryMaxAttempts() {
        return taskRetryMaxAttempts;
    }

    /**
     * setTaskRetryMaxAttempts 기능을 수행합니다.
     *
     * @param taskRetryMaxAttempts 입력 값
     */

    public void setTaskRetryMaxAttempts(final int taskRetryMaxAttempts) {
        this.taskRetryMaxAttempts = taskRetryMaxAttempts;
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
     * getReplyRetryMaxAttempts 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public int getReplyRetryMaxAttempts() {
        return replyRetryMaxAttempts;
    }

    /**
     * setReplyRetryMaxAttempts 기능을 수행합니다.
     *
     * @param replyRetryMaxAttempts 입력 값
     */

    public void setReplyRetryMaxAttempts(final int replyRetryMaxAttempts) {
        this.replyRetryMaxAttempts = replyRetryMaxAttempts;
    }

    /**
     * getReplyRetryBackoffMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getReplyRetryBackoffMs() {
        return replyRetryBackoffMs;
    }

    /**
     * setReplyRetryBackoffMs 기능을 수행합니다.
     *
     * @param replyRetryBackoffMs 입력 값
     */

    public void setReplyRetryBackoffMs(final long replyRetryBackoffMs) {
        this.replyRetryBackoffMs = replyRetryBackoffMs;
    }

    /**
     * requireText 기능을 수행합니다.
     *
     * @param key 입력 값
     * @param value 입력 값
     */

    private static void requireText(final String key, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required");
        }
    }

    /**
     * requirePositive 기능을 수행합니다.
     *
     * @param key 입력 값
     * @param value 입력 값
     */

    private static void requirePositive(final String key, final Number value) {
        if (value == null || value.longValue() <= 0L) {
            throw new IllegalStateException(key + " must be > 0");
        }
    }

    /**
     * requireNonNegative 기능을 수행합니다.
     *
     * @param key 입력 값
     * @param value 입력 값
     */

    private static void requireNonNegative(final String key, final Number value) {
        if (value == null || value.longValue() < 0L) {
            throw new IllegalStateException(key + " must be >= 0");
        }
    }
}

