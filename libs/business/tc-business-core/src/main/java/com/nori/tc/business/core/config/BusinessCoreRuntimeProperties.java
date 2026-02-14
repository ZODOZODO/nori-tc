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
        log.info("BusinessCoreRuntimeProperties validated. consumeTopics=[{},{},{}], produceTopics=[{},{},{}], workerThreads={}",
                kafka.eqpEventsTopic,
                kafka.mesEventsTopic,
                kafka.uiEventsTopic,
                kafka.eqpCommandsTopic,
                kafka.mesCommandsTopic,
                kafka.uiCommandsTopic,
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

        private String eqpEventsTopic = "tc.eqp.events";
        private String mesEventsTopic = "tc.mes.events";
        private String uiEventsTopic = "tc.ui.events";
        private String eqpCommandsTopic = "tc.eqp.commands";
        private String mesCommandsTopic = "tc.mes.commands";
        private String uiCommandsTopic = "tc.ui.commands";

        private int eqpEventsConsumerThreads = 1;
        private int mesEventsConsumerThreads = 1;
        private int uiEventsConsumerThreads = 1;

        private void validate() {
            requireText("tc.business.core.kafka.eqp-events-topic", eqpEventsTopic);
            requireText("tc.business.core.kafka.mes-events-topic", mesEventsTopic);
            requireText("tc.business.core.kafka.ui-events-topic", uiEventsTopic);
            requireText("tc.business.core.kafka.eqp-commands-topic", eqpCommandsTopic);
            requireText("tc.business.core.kafka.mes-commands-topic", mesCommandsTopic);
            requireText("tc.business.core.kafka.ui-commands-topic", uiCommandsTopic);

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

        private int dispatcherThreads = 1;
        private int workerThreads = 8;
        private int timeoutSchedulerThreads = 1;

        private int topicQueueCapacity = 10_000;
        private int mailboxCapacity = 2_048;
        private int ackDrainMaxBatch = 512;

        private long taskTimeoutMs = 180_000L;
        private int retryMaxAttempts = 3;
        private long retryBackoffMs = 200L;

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

    private static void requireEqualsOne(final String key, final int value) {
        if (value != 1) {
            throw new IllegalStateException(key + " must be exactly 1");
        }
    }
}

