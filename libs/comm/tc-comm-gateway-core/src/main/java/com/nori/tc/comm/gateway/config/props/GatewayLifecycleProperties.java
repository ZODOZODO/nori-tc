package com.nori.tc.comm.gateway.config.props;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway lifecycle 상태머신 실행 정책을 정의하는 프로퍼티입니다.
 *
 * <p>prefix: {@code tc.comm.gateway.lifecycle}</p>
 *
 * <p>관리 항목:</p>
 * <p>1) eqpId 단위 lifecycle 이벤트 mailbox 용량</p>
 * <p>2) lifecycle 상태머신 worker 스레드 수</p>
 * <p>3) timeout 감시 스케줄러 스레드 수</p>
 * <p>4) 요청 timeout 값이 비정상일 때 사용할 기본 timeout(ms)</p>
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.lifecycle")
public class GatewayLifecycleProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewayLifecycleProperties.class);

    /**
     * eqpId 단위 lifecycle 이벤트 mailbox 용량입니다.
     */
    private Integer eventMailboxCapacity;

    /**
     * lifecycle 상태머신 worker 스레드 수입니다.
     */
    private Integer workerThreads;

    /**
     * timeout 감시 스케줄러 스레드 수입니다.
     */
    private Integer timeoutSchedulerThreads;

    /**
     * timeout 값이 0 이하로 들어올 때 적용할 기본 timeout(ms)입니다.
     */
    private Long defaultTimeoutMs;

    /**
     * 애플리케이션 시작 시 프로퍼티 값 유효성을 검증합니다.
     */
    @PostConstruct
    public void validate() {
        // 라이프사이클 이벤트 적재 큐 용량이 0이면 요청 수용 자체가 불가능합니다.
        requirePositive("tc.comm.gateway.lifecycle.event-mailbox-capacity", eventMailboxCapacity);
        // 상태머신 worker가 0이면 이벤트가 처리되지 못하므로 양수 강제합니다.
        requirePositive("tc.comm.gateway.lifecycle.worker-threads", workerThreads);
        // timeout 감시용 스케줄러도 최소 1개 이상 필요합니다.
        requirePositive("tc.comm.gateway.lifecycle.timeout-scheduler-threads", timeoutSchedulerThreads);
        // 비정상 timeout 입력 fallback 값은 실제 타임아웃 계산에 쓰이므로 양수여야 합니다.
        requirePositive("tc.comm.gateway.lifecycle.default-timeout-ms", defaultTimeoutMs);

        log.info(
                "GatewayLifecycleProperties validated. eventMailboxCapacity={}, workerThreads={}, timeoutSchedulerThreads={}, defaultTimeoutMs={}",
                eventMailboxCapacity,
                workerThreads,
                timeoutSchedulerThreads,
                defaultTimeoutMs
        );
        if (log.isDebugEnabled()) {
            log.debug(
                    "Gateway lifecycle runtime policy validated. eventMailboxCapacity={}, workerThreads={}, timeoutSchedulerThreads={}, defaultTimeoutMs={}",
                    eventMailboxCapacity,
                    workerThreads,
                    timeoutSchedulerThreads,
                    defaultTimeoutMs
            );
        }
    }

    /**
     * 양수 여부를 검증합니다.
     *
     * @param key 설정 키
     * @param value 검증할 숫자 설정 값
     */
    private static void requirePositive(final String key, final Number value) {
        if (value == null || value.longValue() <= 0L) {
            throw new IllegalStateException(key + " must be > 0");
        }
    }

    /**
     * getEventMailboxCapacity 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public int getEventMailboxCapacity() {
        return eventMailboxCapacity;
    }

    /**
     * setEventMailboxCapacity 프로퍼티 값을 설정합니다.
     *
     * @param eventMailboxCapacity 설정할 프로퍼티 값
     */
    public void setEventMailboxCapacity(final int eventMailboxCapacity) {
        this.eventMailboxCapacity = eventMailboxCapacity;
    }

    /**
     * getWorkerThreads 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public int getWorkerThreads() {
        return workerThreads;
    }

    /**
     * setWorkerThreads 프로퍼티 값을 설정합니다.
     *
     * @param workerThreads 설정할 프로퍼티 값
     */
    public void setWorkerThreads(final int workerThreads) {
        this.workerThreads = workerThreads;
    }

    /**
     * getTimeoutSchedulerThreads 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public int getTimeoutSchedulerThreads() {
        return timeoutSchedulerThreads;
    }

    /**
     * setTimeoutSchedulerThreads 프로퍼티 값을 설정합니다.
     *
     * @param timeoutSchedulerThreads 설정할 프로퍼티 값
     */
    public void setTimeoutSchedulerThreads(final int timeoutSchedulerThreads) {
        this.timeoutSchedulerThreads = timeoutSchedulerThreads;
    }

    /**
     * getDefaultTimeoutMs 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public long getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    /**
     * setDefaultTimeoutMs 프로퍼티 값을 설정합니다.
     *
     * @param defaultTimeoutMs 설정할 프로퍼티 값
     */
    public void setDefaultTimeoutMs(final long defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs;
    }
}
