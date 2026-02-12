package com.nori.tc.messaging.kafka.starter.runtime;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Kafka Consumer 공통 라이프사이클 템플릿입니다.
 *
 * <p>중복되는 실행 흐름(시작/종료, poll, commit retry, lag sampling)을
 * 한 곳에서 관리하고, 실제 레코드 처리만 하위 클래스가 구현하도록 설계했습니다.</p>
 *
 * @param <T> Consumer value 타입
 */
public abstract class AbstractKafkaConsumerLifecycle<T> implements Runnable, SmartLifecycle {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private volatile boolean running = false;
    private volatile long lastLagSampleAt = 0L;
    private KafkaConsumer<String, T> consumer;
    private Thread workerThread;

    /**
     * KafkaConsumer 생성에 사용할 프로퍼티를 반환합니다.
     */
    protected abstract Map<String, Object> consumerProperties();

    /**
     * Consumer 바인딩 모드를 반환합니다.
     */
    protected abstract KafkaConsumerBindingMode bindingMode();

    /**
     * SUBSCRIBE 모드일 때 구독할 topic 목록을 반환합니다.
     */
    protected List<String> subscribeTopics() {
        return List.of();
    }

    /**
     * ASSIGN 모드일 때 할당할 partition 목록을 반환합니다.
     */
    protected List<TopicPartition> assignedPartitions() {
        return List.of();
    }

    /**
     * poll timeout 값을 반환합니다.
     */
    protected abstract Duration pollTimeout();

    /**
     * Consumer worker thread 이름을 반환합니다.
     */
    protected abstract String threadName();

    /**
     * 종료 시 worker join 대기 시간을 반환합니다.
     */
    protected abstract long shutdownWaitMs();

    /**
     * commit 재시도 최대 횟수를 반환합니다.
     */
    protected abstract int commitRetryMax();

    /**
     * commit 재시도 backoff(ms)를 반환합니다.
     */
    protected abstract long commitRetryBackoffMs();

    /**
     * lag 샘플링 주기(ms)를 반환합니다.
     */
    protected abstract long lagSampleIntervalMs();

    /**
     * poll된 단일 레코드를 처리합니다.
     */
    protected abstract void handleRecord(ConsumerRecord<String, T> record);

    /**
     * 로그 출력용 consumer 식별명을 반환합니다.
     */
    protected String consumerName() {
        return getClass().getSimpleName();
    }

    /**
     * commit 실패 시 호출되는 훅입니다.
     */
    protected void onCommitFail(final Exception ex, final int attempt) {
        log.warn("Kafka commit failed. consumer={}, attempt={}", consumerName(), attempt, ex);
    }

    /**
     * 레코드 처리 실패 시 호출되는 훅입니다.
     */
    protected void onRecordFail(final ConsumerRecord<String, T> record, final Exception ex) {
        log.warn("Kafka record handling failed. consumer={}, topic={}, partition={}, offset={}",
                consumerName(), record.topic(), record.partition(), record.offset(), ex);
    }

    /**
     * lag 샘플 지점마다 호출되는 훅입니다.
     */
    protected void onLagSample(final TopicPartition topicPartition, final long lag) {
        // no-op
    }

    /**
     * Consumer 생성 및 바인딩이 끝난 뒤 호출되는 훅입니다.
     */
    protected void afterStart(final KafkaConsumer<String, T> startedConsumer) {
        // no-op
    }

    /**
     * 레코드 처리 실패가 발생했을 때 해당 poll 배치를 커밋할지 여부를 반환합니다.
     *
     * <p>true면 기존 동작(실패가 있어도 commit 시도)을 유지하고,
     * false면 해당 배치 commit을 건너뛰고 재처리 경로로 보냅니다.</p>
     */
    protected boolean commitOnRecordFailure() {
        return true;
    }

    /**
     * 레코드 실패 시 실패한 offset으로 seek 하여 재시도할지 여부를 반환합니다.
     *
     * <p>commitOnRecordFailure=false와 함께 사용해야 의미가 있습니다.</p>
     */
    protected boolean retryFailedRecordFromCurrentOffset() {
        return false;
    }

    /**
     * 실패 레코드 재시도 전 대기 시간(ms)을 반환합니다.
     */
    protected long failedRecordRetryBackoffMs() {
        return 0L;
    }

    /**
     * Consumer 루프를 시작합니다.
     */
    @Override
    public synchronized void start() {
        if (running) {
            if (log.isDebugEnabled()) {
                log.debug("Kafka consumer already running. consumer={}", consumerName());
            }
            return;
        }
        log.info("Kafka consumer starting. consumer={}, mode={}", consumerName(), bindingMode());
        running = true;

        final KafkaConsumer<String, T> createdConsumer = new KafkaConsumer<>(consumerProperties());
        bindConsumer(createdConsumer);
        consumer = createdConsumer;

        workerThread = new Thread(this, threadName());
        workerThread.setDaemon(true);
        workerThread.start();

        afterStart(createdConsumer);
        log.info("Kafka consumer started. consumer={}, thread={}", consumerName(), workerThread.getName());
    }

    /**
     * Consumer 루프를 종료합니다.
     */
    @Override
    public synchronized void stop() {
        running = false;
        log.info("Kafka consumer stopping. consumer={}", consumerName());

        final KafkaConsumer<String, T> current = snapshotConsumer();
        if (current != null) {
            current.wakeup();
        }

        final Thread currentWorker = workerThread;
        if (currentWorker != null) {
            try {
                currentWorker.join(shutdownWaitMs());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        closeConsumerQuietly();
        log.info("Kafka consumer stopped. consumer={}", consumerName());
    }

    /**
     * 현재 구동 여부를 반환합니다.
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * SmartLifecycle phase를 반환합니다.
     */
    @Override
    public int getPhase() {
        return 0;
    }

    /**
     * 실제 poll-processing 루프를 수행합니다.
     */
    @Override
    public void run() {
        final KafkaConsumer<String, T> runningConsumer = snapshotConsumer();
        if (runningConsumer == null) {
            log.debug("Kafka consumer loop skipped (consumer is null). consumer={}", consumerName());
            return;
        }

        try {
            while (running) {
                final ConsumerRecords<String, T> records = runningConsumer.poll(pollTimeout());
                if (records.isEmpty()) {
                    continue;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Kafka records polled. consumer={}, count={}", consumerName(), records.count());
                }

                boolean recordFailed = false;
                ConsumerRecord<String, T> failedRecord = null;
                for (ConsumerRecord<String, T> record : records) {
                    try {
                        handleRecord(record);
                    } catch (Exception ex) {
                        onRecordFail(record, ex);
                        recordFailed = true;
                        failedRecord = record;
                        if (!commitOnRecordFailure()) {
                            break;
                        }
                    }
                }

                if (recordFailed && !commitOnRecordFailure()) {
                    handleFailedRecordRetry(runningConsumer, failedRecord);
                    continue;
                }

                commitWithRetry(runningConsumer);
                sampleLagIfNeeded(runningConsumer);
            }
        } catch (WakeupException ignored) {
            log.debug("Kafka consumer wakeup received. consumer={}", consumerName());
        } finally {
            closeConsumerQuietly();
        }
    }

    /**
     * 모드에 따라 subscribe/assign 바인딩을 수행합니다.
     */
    private void bindConsumer(final KafkaConsumer<String, T> createdConsumer) {
        if (bindingMode() == KafkaConsumerBindingMode.SUBSCRIBE) {
            final List<String> topics = subscribeTopics();
            if (topics == null || topics.isEmpty()) {
                throw new IllegalStateException("subscribeTopics must not be empty in SUBSCRIBE mode");
            }
            createdConsumer.subscribe(topics);
            if (log.isDebugEnabled()) {
                log.debug("Kafka consumer subscribed. consumer={}, topics={}", consumerName(), topics);
            }
            return;
        }

        final List<TopicPartition> partitions = assignedPartitions();
        if (partitions == null || partitions.isEmpty()) {
            throw new IllegalStateException("assignedPartitions must not be empty in ASSIGN mode");
        }
        createdConsumer.assign(partitions);
        if (!createdConsumer.subscription().isEmpty()) {
            throw new IllegalStateException("ASSIGN mode consumer must not have subscriptions");
        }
        if (log.isDebugEnabled()) {
            log.debug("Kafka consumer assigned. consumer={}, partitions={}", consumerName(), partitions);
        }
    }

    /**
     * 배치 commit을 retry 정책에 따라 수행합니다.
     */
    private void commitWithRetry(final KafkaConsumer<String, T> runningConsumer) {
        int attempt = 0;
        while (true) {
            try {
                runningConsumer.commitSync();
                if (log.isDebugEnabled()) {
                    log.debug("Kafka commit success. consumer={}", consumerName());
                }
                return;
            } catch (Exception ex) {
                onCommitFail(ex, attempt);
                if (attempt >= commitRetryMax()) {
                    return;
                }
                attempt++;
                final long backoffMs = commitRetryBackoffMs();
                if (backoffMs > 0) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    /**
     * 주기적으로 consumer lag를 샘플링합니다.
     */
    private void sampleLagIfNeeded(final KafkaConsumer<String, T> runningConsumer) {
        final long intervalMs = lagSampleIntervalMs();
        if (intervalMs <= 0) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now - lastLagSampleAt < intervalMs) {
            return;
        }
        lastLagSampleAt = now;

        try {
            final var assignment = runningConsumer.assignment();
            if (assignment.isEmpty()) {
                return;
            }
            final Map<TopicPartition, Long> endOffsets = runningConsumer.endOffsets(assignment);
            for (TopicPartition topicPartition : assignment) {
                final long position = runningConsumer.position(topicPartition);
                final long endOffset = endOffsets.getOrDefault(topicPartition, position);
                onLagSample(topicPartition, Math.max(0L, endOffset - position));
            }
        } catch (Exception ignored) {
            // lag 샘플링 실패는 처리 흐름을 중단하지 않습니다.
        }
    }

    /**
     * 레코드 실패 시 재시도 전략(seek + backoff)을 수행합니다.
     */
    private void handleFailedRecordRetry(
            final KafkaConsumer<String, T> runningConsumer,
            final ConsumerRecord<String, T> failedRecord
    ) {
        if (failedRecord == null) {
            return;
        }

        if (retryFailedRecordFromCurrentOffset()) {
            final TopicPartition topicPartition = new TopicPartition(failedRecord.topic(), failedRecord.partition());
            runningConsumer.seek(topicPartition, failedRecord.offset());
            if (log.isDebugEnabled()) {
                log.debug("Kafka consumer seek to failed record. consumer={}, topic={}, partition={}, offset={}",
                        consumerName(),
                        failedRecord.topic(),
                        failedRecord.partition(),
                        failedRecord.offset());
            }
        }

        final long retryBackoffMs = failedRecordRetryBackoffMs();
        if (retryBackoffMs > 0L) {
            try {
                Thread.sleep(retryBackoffMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 현재 consumer 참조를 스냅샷으로 가져옵니다.
     */
    private synchronized KafkaConsumer<String, T> snapshotConsumer() {
        return consumer;
    }

    /**
     * consumer를 안전하게 닫습니다.
     */
    private synchronized void closeConsumerQuietly() {
        final KafkaConsumer<String, T> current = consumer;
        consumer = null;
        if (current != null) {
            current.close();
        }
    }
}
