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

    /**
     * getKafka 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public Kafka getKafka() {
        return kafka;
    }

    /**
     * getRuntime 기능을 수행합니다.
     *
     * @return 처리 결과
     */

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

        /**
         * validate 기능을 수행합니다.
         *
         */

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

        /**
         * getEqpEventsTopic 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public String getEqpEventsTopic() {
            return eqpEventsTopic;
        }

        /**
         * setEqpEventsTopic 기능을 수행합니다.
         *
         * @param eqpEventsTopic 입력 값
         */

        public void setEqpEventsTopic(final String eqpEventsTopic) {
            this.eqpEventsTopic = eqpEventsTopic;
        }

        /**
         * getMesEventsTopic 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public String getMesEventsTopic() {
            return mesEventsTopic;
        }

        /**
         * setMesEventsTopic 기능을 수행합니다.
         *
         * @param mesEventsTopic 입력 값
         */

        public void setMesEventsTopic(final String mesEventsTopic) {
            this.mesEventsTopic = mesEventsTopic;
        }

        /**
         * getUiEventsTopic 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public String getUiEventsTopic() {
            return uiEventsTopic;
        }

        /**
         * setUiEventsTopic 기능을 수행합니다.
         *
         * @param uiEventsTopic 입력 값
         */

        public void setUiEventsTopic(final String uiEventsTopic) {
            this.uiEventsTopic = uiEventsTopic;
        }

        /**
         * getEqpCommandsTopic 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public String getEqpCommandsTopic() {
            return eqpCommandsTopic;
        }

        /**
         * setEqpCommandsTopic 기능을 수행합니다.
         *
         * @param eqpCommandsTopic 입력 값
         */

        public void setEqpCommandsTopic(final String eqpCommandsTopic) {
            this.eqpCommandsTopic = eqpCommandsTopic;
        }

        /**
         * getMesCommandsTopic 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public String getMesCommandsTopic() {
            return mesCommandsTopic;
        }

        /**
         * setMesCommandsTopic 기능을 수행합니다.
         *
         * @param mesCommandsTopic 입력 값
         */

        public void setMesCommandsTopic(final String mesCommandsTopic) {
            this.mesCommandsTopic = mesCommandsTopic;
        }

        /**
         * getUiCommandsTopic 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public String getUiCommandsTopic() {
            return uiCommandsTopic;
        }

        /**
         * setUiCommandsTopic 기능을 수행합니다.
         *
         * @param uiCommandsTopic 입력 값
         */

        public void setUiCommandsTopic(final String uiCommandsTopic) {
            this.uiCommandsTopic = uiCommandsTopic;
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
         * getEqpEventsConsumerThreads 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public int getEqpEventsConsumerThreads() {
            return eqpEventsConsumerThreads;
        }

        /**
         * setEqpEventsConsumerThreads 기능을 수행합니다.
         *
         * @param eqpEventsConsumerThreads 입력 값
         */

        public void setEqpEventsConsumerThreads(final int eqpEventsConsumerThreads) {
            this.eqpEventsConsumerThreads = eqpEventsConsumerThreads;
        }

        /**
         * getMesEventsConsumerThreads 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public int getMesEventsConsumerThreads() {
            return mesEventsConsumerThreads;
        }

        /**
         * setMesEventsConsumerThreads 기능을 수행합니다.
         *
         * @param mesEventsConsumerThreads 입력 값
         */

        public void setMesEventsConsumerThreads(final int mesEventsConsumerThreads) {
            this.mesEventsConsumerThreads = mesEventsConsumerThreads;
        }

        /**
         * getUiEventsConsumerThreads 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public int getUiEventsConsumerThreads() {
            return uiEventsConsumerThreads;
        }

        /**
         * setUiEventsConsumerThreads 기능을 수행합니다.
         *
         * @param uiEventsConsumerThreads 입력 값
         */

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

        /**
         * validate 기능을 수행합니다.
         *
         */

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
         * getTimeoutSchedulerThreads 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public int getTimeoutSchedulerThreads() {
            return timeoutSchedulerThreads;
        }

        /**
         * setTimeoutSchedulerThreads 기능을 수행합니다.
         *
         * @param timeoutSchedulerThreads 입력 값
         */

        public void setTimeoutSchedulerThreads(final int timeoutSchedulerThreads) {
            this.timeoutSchedulerThreads = timeoutSchedulerThreads;
        }

        /**
         * getTopicQueueCapacity 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public int getTopicQueueCapacity() {
            return topicQueueCapacity;
        }

        /**
         * setTopicQueueCapacity 기능을 수행합니다.
         *
         * @param topicQueueCapacity 입력 값
         */

        public void setTopicQueueCapacity(final int topicQueueCapacity) {
            this.topicQueueCapacity = topicQueueCapacity;
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
         * getAckDrainMaxBatch 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public int getAckDrainMaxBatch() {
            return ackDrainMaxBatch;
        }

        /**
         * setAckDrainMaxBatch 기능을 수행합니다.
         *
         * @param ackDrainMaxBatch 입력 값
         */

        public void setAckDrainMaxBatch(final int ackDrainMaxBatch) {
            this.ackDrainMaxBatch = ackDrainMaxBatch;
        }

        /**
         * getTaskTimeoutMs 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public long getTaskTimeoutMs() {
            return taskTimeoutMs;
        }

        /**
         * setTaskTimeoutMs 기능을 수행합니다.
         *
         * @param taskTimeoutMs 입력 값
         */

        public void setTaskTimeoutMs(final long taskTimeoutMs) {
            this.taskTimeoutMs = taskTimeoutMs;
        }

        /**
         * getRetryMaxAttempts 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public int getRetryMaxAttempts() {
            return retryMaxAttempts;
        }

        /**
         * setRetryMaxAttempts 기능을 수행합니다.
         *
         * @param retryMaxAttempts 입력 값
         */

        public void setRetryMaxAttempts(final int retryMaxAttempts) {
            this.retryMaxAttempts = retryMaxAttempts;
        }

        /**
         * getRetryBackoffMs 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public long getRetryBackoffMs() {
            return retryBackoffMs;
        }

        /**
         * setRetryBackoffMs 기능을 수행합니다.
         *
         * @param retryBackoffMs 입력 값
         */

        public void setRetryBackoffMs(final long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
        }
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

    /**
     * requireEqualsOne 기능을 수행합니다.
     *
     * @param key 입력 값
     * @param value 입력 값
     */

    private static void requireEqualsOne(final String key, final Number value) {
        if (value == null || value.intValue() != 1) {
            throw new IllegalStateException(key + " must be exactly 1");
        }
    }
}

