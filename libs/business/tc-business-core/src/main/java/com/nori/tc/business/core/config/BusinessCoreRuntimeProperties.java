package com.nori.tc.business.core.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * tc-business-core-app 런타임 설정 프로퍼티입니다.
 *
 * <p>설정 prefix는 {@code tc.business.core}입니다.</p>
 */
@ConfigurationProperties(prefix = "tc.business.core")
public class BusinessCoreRuntimeProperties {

    private static final Logger log = LoggerFactory.getLogger(BusinessCoreRuntimeProperties.class);

    private final Kafka kafka = new Kafka();
    private final Runtime runtime = new Runtime();

    /**
     * 프로퍼티 유효성 검증을 수행합니다.
     */
    @PostConstruct
    public void validate() {
        kafka.validate();
        runtime.validate();
        log.info("BusinessCoreRuntimeProperties validated. consumeTopics=[{},{},{}], produceTopics=[{},{},{}], source={}, workerThreads={}",
                kafka.eqpEventsTopic,
                kafka.mesEventsTopic,
                kafka.uiEventsTopic,
                kafka.eqpCommandsTopic,
                kafka.mesCommandsTopic,
                kafka.uiCommandsTopic,
                kafka.source,
                runtime.workerThreads);
    }

    public Kafka getKafka() {
        return kafka;
    }

    public Runtime getRuntime() {
        return runtime;
    }

    /**
     * Kafka 소비 토픽 및 consumer thread 설정입니다.
     */
    public static final class Kafka {

        /**
         * EQP 이벤트 소비 토픽입니다.
         */
        private String eqpEventsTopic;

        /**
         * MES 이벤트 소비 토픽입니다.
         */
        private String mesEventsTopic;

        /**
         * UI 이벤트 소비 토픽입니다.
         */
        private String uiEventsTopic;

        /**
         * EQP 명령 발행 토픽입니다.
         */
        private String eqpCommandsTopic;

        /**
         * MES 명령 발행 토픽입니다.
         */
        private String mesCommandsTopic;

        /**
         * UI 명령 발행 토픽입니다.
         */
        private String uiCommandsTopic;

        /**
         * Kafka metadata.source 값입니다.
         */
        private String source;

        /**
         * EQP 이벤트 토픽 consumer thread 수입니다.
         */
        private Integer eqpEventsConsumerThreads;

        /**
         * MES 이벤트 토픽 consumer thread 수입니다.
         */
        private Integer mesEventsConsumerThreads;

        /**
         * UI 이벤트 토픽 consumer thread 수입니다.
         */
        private Integer uiEventsConsumerThreads;

        private void validate() {
            requireText("tc.business.core.kafka.eqp-events-topic", eqpEventsTopic);
            requireText("tc.business.core.kafka.mes-events-topic", mesEventsTopic);
            requireText("tc.business.core.kafka.ui-events-topic", uiEventsTopic);
            requireText("tc.business.core.kafka.eqp-commands-topic", eqpCommandsTopic);
            requireText("tc.business.core.kafka.mes-commands-topic", mesCommandsTopic);
            requireText("tc.business.core.kafka.ui-commands-topic", uiCommandsTopic);
            requireText("tc.business.core.kafka.source", source);

            /*
             * 현재 설계 요구사항은 토픽별 consumer thread 1개 고정입니다.
             * 추후 확장 시 정책 변경 문서와 함께 본 제약을 수정해야 합니다.
             */
            requireEqualsOne("tc.business.core.kafka.eqp-events-consumer-threads", eqpEventsConsumerThreads);
            requireEqualsOne("tc.business.core.kafka.mes-events-consumer-threads", mesEventsConsumerThreads);
            requireEqualsOne("tc.business.core.kafka.ui-events-consumer-threads", uiEventsConsumerThreads);
        }

        public String getEqpEventsTopic() {
            return eqpEventsTopic;
        }

        public void setEqpEventsTopic(final String eqpEventsTopic) {
            this.eqpEventsTopic = eqpEventsTopic;
        }

        public String getMesEventsTopic() {
            return mesEventsTopic;
        }

        public void setMesEventsTopic(final String mesEventsTopic) {
            this.mesEventsTopic = mesEventsTopic;
        }

        public String getUiEventsTopic() {
            return uiEventsTopic;
        }

        public void setUiEventsTopic(final String uiEventsTopic) {
            this.uiEventsTopic = uiEventsTopic;
        }

        public String getEqpCommandsTopic() {
            return eqpCommandsTopic;
        }

        public void setEqpCommandsTopic(final String eqpCommandsTopic) {
            this.eqpCommandsTopic = eqpCommandsTopic;
        }

        public String getMesCommandsTopic() {
            return mesCommandsTopic;
        }

        public void setMesCommandsTopic(final String mesCommandsTopic) {
            this.mesCommandsTopic = mesCommandsTopic;
        }

        public String getUiCommandsTopic() {
            return uiCommandsTopic;
        }

        public void setUiCommandsTopic(final String uiCommandsTopic) {
            this.uiCommandsTopic = uiCommandsTopic;
        }

        public String getSource() {
            return source;
        }

        public void setSource(final String source) {
            this.source = source;
        }

        public int getEqpEventsConsumerThreads() {
            return eqpEventsConsumerThreads;
        }

        public void setEqpEventsConsumerThreads(final int eqpEventsConsumerThreads) {
            this.eqpEventsConsumerThreads = eqpEventsConsumerThreads;
        }

        public int getMesEventsConsumerThreads() {
            return mesEventsConsumerThreads;
        }

        public void setMesEventsConsumerThreads(final int mesEventsConsumerThreads) {
            this.mesEventsConsumerThreads = mesEventsConsumerThreads;
        }

        public int getUiEventsConsumerThreads() {
            return uiEventsConsumerThreads;
        }

        public void setUiEventsConsumerThreads(final int uiEventsConsumerThreads) {
            this.uiEventsConsumerThreads = uiEventsConsumerThreads;
        }
    }

    /**
     * Dispatcher/Worker/Queue/Retry/Timeout 런타임 설정입니다.
     */
    public static final class Runtime {

        /**
         * 디스패처 스레드 수입니다.
         */
        private Integer dispatcherThreads;

        /**
         * 워커 스레드 수입니다.
         */
        private Integer workerThreads;

        /**
         * 타임아웃 스케줄러 스레드 수입니다.
         */
        private Integer timeoutSchedulerThreads;

        /**
         * 토픽 큐 용량입니다.
         */
        private Integer topicQueueCapacity;

        /**
         * 장비별 메일박스 용량입니다.
         */
        private Integer mailboxCapacity;

        /**
         * ACK 드레인 최대 배치 크기입니다.
         */
        private Integer ackDrainMaxBatch;

        /**
         * 작업 타임아웃(ms)입니다.
         */
        private Long taskTimeoutMs;

        /**
         * 재시도 최대 횟수입니다.
         */
        private Integer retryMaxAttempts;

        /**
         * 재시도 backoff(ms)입니다.
         */
        private Long retryBackoffMs;

        private void validate() {
            requirePositive("tc.business.core.runtime.dispatcher-threads", dispatcherThreads);
            requirePositive("tc.business.core.runtime.worker-threads", workerThreads);
            requirePositive("tc.business.core.runtime.timeout-scheduler-threads", timeoutSchedulerThreads);

            requirePositive("tc.business.core.runtime.topic-queue-capacity", topicQueueCapacity);
            requirePositive("tc.business.core.runtime.mailbox-capacity", mailboxCapacity);
            requirePositive("tc.business.core.runtime.ack-drain-max-batch", ackDrainMaxBatch);

            requirePositive("tc.business.core.runtime.task-timeout-ms", taskTimeoutMs);
            requirePositive("tc.business.core.runtime.retry-max-attempts", retryMaxAttempts);
            requireNonNegative("tc.business.core.runtime.retry-backoff-ms", retryBackoffMs);
        }

        public int getDispatcherThreads() {
            return dispatcherThreads;
        }

        public void setDispatcherThreads(final int dispatcherThreads) {
            this.dispatcherThreads = dispatcherThreads;
        }

        public int getWorkerThreads() {
            return workerThreads;
        }

        public void setWorkerThreads(final int workerThreads) {
            this.workerThreads = workerThreads;
        }

        public int getTimeoutSchedulerThreads() {
            return timeoutSchedulerThreads;
        }

        public void setTimeoutSchedulerThreads(final int timeoutSchedulerThreads) {
            this.timeoutSchedulerThreads = timeoutSchedulerThreads;
        }

        public int getTopicQueueCapacity() {
            return topicQueueCapacity;
        }

        public void setTopicQueueCapacity(final int topicQueueCapacity) {
            this.topicQueueCapacity = topicQueueCapacity;
        }

        public int getMailboxCapacity() {
            return mailboxCapacity;
        }

        public void setMailboxCapacity(final int mailboxCapacity) {
            this.mailboxCapacity = mailboxCapacity;
        }

        public int getAckDrainMaxBatch() {
            return ackDrainMaxBatch;
        }

        public void setAckDrainMaxBatch(final int ackDrainMaxBatch) {
            this.ackDrainMaxBatch = ackDrainMaxBatch;
        }

        public long getTaskTimeoutMs() {
            return taskTimeoutMs;
        }

        public void setTaskTimeoutMs(final long taskTimeoutMs) {
            this.taskTimeoutMs = taskTimeoutMs;
        }

        public int getRetryMaxAttempts() {
            return retryMaxAttempts;
        }

        public void setRetryMaxAttempts(final int retryMaxAttempts) {
            this.retryMaxAttempts = retryMaxAttempts;
        }

        public long getRetryBackoffMs() {
            return retryBackoffMs;
        }

        public void setRetryBackoffMs(final long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
        }
    }

    private static void requireText(final String key, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required");
        }
    }

    private static void requirePositive(final String key, final Number value) {
        if (value == null || value.longValue() <= 0L) {
            throw new IllegalStateException(key + " must be > 0");
        }
    }

    private static void requireNonNegative(final String key, final Number value) {
        if (value == null || value.longValue() < 0L) {
            throw new IllegalStateException(key + " must be >= 0");
        }
    }

    private static void requireEqualsOne(final String key, final Number value) {
        if (value == null || value.intValue() != 1) {
            throw new IllegalStateException(key + " must be exactly 1");
        }
    }
}

