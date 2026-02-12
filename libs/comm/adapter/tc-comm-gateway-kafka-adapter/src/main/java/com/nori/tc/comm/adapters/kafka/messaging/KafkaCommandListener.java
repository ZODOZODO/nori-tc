package com.nori.tc.comm.adapters.kafka.messaging;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaClientProperties;
import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.gateway.metrics.GatewayLogContext;
import com.nori.tc.comm.gateway.metrics.GatewayLogSampler;
import com.nori.tc.comm.gateway.metrics.GatewayMetrics;
import com.nori.tc.messaging.kafka.starter.contract.KafkaCommandDispatcher;
import com.nori.tc.messaging.kafka.starter.contract.KafkaCommandMessage;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Single-thread Kafka consumer for UI commands.
 *
 * Threading model:
 * - One consumer loop per gateway instance.
 * - Poll -> dispatch -> batch commit (commitSync per poll batch).
 * - Drop is a normal outcome, so the offset is committed.
 */
@Component
public class KafkaCommandListener implements Runnable, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(KafkaCommandListener.class);
    private final GatewayKafkaClientProperties kafkaClientProperties;
    private final GatewayKafkaTopicProperties topicProperties;
    private final com.nori.tc.comm.gateway.config.GatewayKafkaShardProperties shardProperties;
    private final KafkaCommandDispatcher dispatcher;
    private final GatewayMetrics metrics;
    private final GatewayLogSampler logSampler;

    private KafkaConsumer<String, KafkaCommandMessage> consumer;
    private Thread workerThread;
    private volatile boolean running = false;
    private volatile long lastLagSampleAt = 0L;

    
    /**
     * 게이트웨이 Kafka 어댑터 구성 요소를 초기화합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param kafkaClientProperties 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     * @param topicProperties Kafka 토픽 이름
     * @param shardProperties 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     * @param dispatcher 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     * @param metrics 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     * @param logSampler 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     */
    public KafkaCommandListener(
            final GatewayKafkaClientProperties kafkaClientProperties,
            final GatewayKafkaTopicProperties topicProperties,
            final com.nori.tc.comm.gateway.config.GatewayKafkaShardProperties shardProperties,
            final KafkaCommandDispatcher dispatcher,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler
    ) {
        this.kafkaClientProperties = Objects.requireNonNull(kafkaClientProperties, "kafkaClientProperties is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.shardProperties = Objects.requireNonNull(shardProperties, "shardProperties is null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 실행 환경을 초기화하고 기동합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     */
    @Override
    public void start() {
        // 라이프사이클 단계: 자원 초기화/해제 순서를 보장합니다.
        if (running) {
            return;
        }
        running = true;

        consumer = new KafkaConsumer<>(consumerProps());
        consumer.subscribe(List.of(topicProperties.getUiCommands()));

        workerThread = new Thread(this, "kafka-ui-commands-consumer");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("Kafka UI command consumer started. topic={}, thread={}",
                topicProperties.getUiCommands(), workerThread.getName());
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 리소스를 정리하고 종료합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     */
    @Override
    public void stop() {
        // 라이프사이클 단계: 자원 초기화/해제 순서를 보장합니다.
        running = false;
        if (consumer != null) {
            consumer.wakeup();
        }
        if (workerThread != null) {
            try {
                workerThread.join(shardProperties.getConsumerShutdownWaitMs());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        if (consumer != null) {
            consumer.close();
        }
        log.info("Kafka UI command consumer stopped.");
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 처리 성공 여부
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    @Override
    public int getPhase() {
        return 0;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     */
    @Override
    public void run() {
        // 처리 단계: 분기 조건에 따라 흐름을 제어하고 후속 작업을 호출합니다.
        try {
            while (running) {
                final Duration pollTimeout = Duration.ofMillis(shardProperties.getUiPollTimeoutMs());
                final ConsumerRecords<String, KafkaCommandMessage> records = consumer.poll(pollTimeout);
                if (records.isEmpty()) {
                    continue;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Kafka UI commands polled. count={}", records.count());
                }

                records.forEach(record -> {
                    final String key = record.key();
                    final KafkaCommandMessage message = record.value();
                    final String eqpIdForLog = (message != null && message.equipmentId() != null)
                            ? message.equipmentId()
                            : key;
                    final String traceIdForLog = message == null ? null : message.traceId();
                    try (GatewayLogContext ignored = GatewayLogContext.withEqpAndTraceId(eqpIdForLog, traceIdForLog)) {

                        if (message == null) {
                            if (logSampler.shouldLogCommandDrop()) {
                                log.warn("UI command drop (null message). topic={}, partition={}, offset={}",
                                        record.topic(), record.partition(), record.offset());
                            }
                            return;
                        }
                        // Spec: command key must match eqpId
                        if (key == null || !key.equals(message.equipmentId())) {
                            if (logSampler.shouldLogCommandDrop()) {
                                log.warn("UI command drop (key mismatch). key={}, eqpId={}", key, message.equipmentId());
                            }
                            return;
                        }

                        dispatcher.dispatch(message);
                    } catch (Exception ex) {
                        log.warn("UI command dispatch failed. topic={}, partition={}, offset={}",
                                record.topic(), record.partition(), record.offset(), ex);
                    }
                });

                // Drop is a normal path, so commit the batch.
                commitWithRetry();

                // lag sampling (low frequency)
                sampleLagIfNeeded();
            }
        } catch (WakeupException ignored) {
            // shutdown
        } finally {
            if (consumer != null) {
                consumer.close();
            }
        }
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 입력 이벤트/요청을 처리합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    private Map<String, Object> consumerProps() {
        return kafkaClientProperties.buildConsumerProperties();
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     */
    private void commitWithRetry() {
        final int maxRetry = shardProperties.getCommitRetryMax();
        final long backoffMs = shardProperties.getCommitRetryBackoffMs();

        int attempt = 0;
        while (true) {
            try {
                consumer.commitSync();
                if (log.isDebugEnabled()) {
                    log.debug("Kafka commit success (ui.commands).");
                }
                return;
            } catch (Exception ex) {
                metrics.incrementKafkaCommitFail();
                if (logSampler.shouldLogCommitFail()) {
                    log.warn("Kafka commit failed (ui.commands). attempt={}", attempt, ex);
                }
                if (attempt >= maxRetry) {
                    return;
                }
                attempt++;
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
     * 게이트웨이 Kafka 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     */
    private void sampleLagIfNeeded() {
        final long now = System.currentTimeMillis();
        final long interval = shardProperties.getLagSampleIntervalMs();
        if (now - lastLagSampleAt < interval) {
            return;
        }
        lastLagSampleAt = now;

        try {
            final var assignment = consumer.assignment();
            if (assignment.isEmpty()) {
                return;
            }
            final Map<org.apache.kafka.common.TopicPartition, Long> endOffsets = consumer.endOffsets(assignment);
            for (org.apache.kafka.common.TopicPartition tp : assignment) {
                final long position = consumer.position(tp);
                final long end = endOffsets.getOrDefault(tp, position);
                final long lag = Math.max(0L, end - position);
                metrics.recordConsumerLag(tp.topic(), tp.partition(), lag);
            }
        } catch (Exception ex) {
            // ignore lag sampling errors
        }
    }
}
