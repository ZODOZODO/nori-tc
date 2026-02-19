package com.nori.tc.business.adapters.kafka.ui;

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
    private String source = "TC-BUSINESS-CORE-APP";

    /**
     * traceId 중복 처리 TTL(ms)입니다.
     */
    private long duplicateTraceTtlMs = 600_000L;

    /**
     * traceId 중복 처리 캐시 최대 엔트리 수입니다.
     */
    private int duplicateTraceMaxSize = 100_000;

    /**
     * UI task 처리 재시도 최대 시도 횟수입니다.
     */
    private int taskRetryMaxAttempts = 3;

    /**
     * UI task 처리 재시도 backoff(ms)입니다.
     */
    private long taskRetryBackoffMs = 200L;

    /**
     * UI REP 발행 재시도 최대 시도 횟수입니다.
     */
    private int replyRetryMaxAttempts = 3;

    /**
     * UI REP 발행 재시도 backoff(ms)입니다.
     */
    private long replyRetryBackoffMs = 200L;

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

    public String getSource() {
        return source;
    }

    public void setSource(final String source) {
        this.source = source;
    }

    public long getDuplicateTraceTtlMs() {
        return duplicateTraceTtlMs;
    }

    public void setDuplicateTraceTtlMs(final long duplicateTraceTtlMs) {
        this.duplicateTraceTtlMs = duplicateTraceTtlMs;
    }

    public int getDuplicateTraceMaxSize() {
        return duplicateTraceMaxSize;
    }

    public void setDuplicateTraceMaxSize(final int duplicateTraceMaxSize) {
        this.duplicateTraceMaxSize = duplicateTraceMaxSize;
    }

    public int getTaskRetryMaxAttempts() {
        return taskRetryMaxAttempts;
    }

    public void setTaskRetryMaxAttempts(final int taskRetryMaxAttempts) {
        this.taskRetryMaxAttempts = taskRetryMaxAttempts;
    }

    public long getTaskRetryBackoffMs() {
        return taskRetryBackoffMs;
    }

    public void setTaskRetryBackoffMs(final long taskRetryBackoffMs) {
        this.taskRetryBackoffMs = taskRetryBackoffMs;
    }

    public int getReplyRetryMaxAttempts() {
        return replyRetryMaxAttempts;
    }

    public void setReplyRetryMaxAttempts(final int replyRetryMaxAttempts) {
        this.replyRetryMaxAttempts = replyRetryMaxAttempts;
    }

    public long getReplyRetryBackoffMs() {
        return replyRetryBackoffMs;
    }

    public void setReplyRetryBackoffMs(final long replyRetryBackoffMs) {
        this.replyRetryBackoffMs = replyRetryBackoffMs;
    }

    private static void requireText(final String key, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required");
        }
    }

    private static void requirePositive(final String key, final long value) {
        if (value <= 0L) {
            throw new IllegalStateException(key + " must be > 0");
        }
    }

    private static void requireNonNegative(final String key, final long value) {
        if (value < 0L) {
            throw new IllegalStateException(key + " must be >= 0");
        }
    }
}

