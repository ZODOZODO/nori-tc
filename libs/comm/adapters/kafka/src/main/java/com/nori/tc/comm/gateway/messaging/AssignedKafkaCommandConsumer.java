package com.nori.tc.apps.commgateway.messaging;

import com.nori.tc.apps.commgateway.config.GatewayKafkaClientProperties;
import com.nori.tc.apps.commgateway.config.GatewayKafkaShardProperties;
import com.nori.tc.apps.commgateway.config.GatewayKafkaTopicProperties;
import com.nori.tc.apps.commgateway.metrics.GatewayLogContext;
import com.nori.tc.apps.commgateway.metrics.GatewayLogSampler;
import com.nori.tc.apps.commgateway.metrics.GatewayMetrics;
import com.nori.tc.messaging.kafka.starter.contract.KafkaCommandDispatcher;
import com.nori.tc.messaging.kafka.starter.contract.KafkaCommandMessage;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Assigned Kafka consumer for tc.eqp.commands (no rebalancing).
 */
@Component
public class AssignedKafkaCommandConsumer implements Runnable, org.springframework.context.SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AssignedKafkaCommandConsumer.class);

    private final GatewayKafkaClientProperties kafkaClientProperties;
    private final GatewayKafkaShardProperties shardProperties;
    private final GatewayKafkaTopicProperties topicProperties;
    private final KafkaCommandDispatcher dispatcher;
    private final GatewayMetrics metrics;
    private final GatewayLogSampler logSampler;

    private KafkaConsumer<String, KafkaCommandMessage> consumer;
    private Thread workerThread;
    private volatile boolean running = false;
    private volatile long lastLagSampleAt = 0L;

    public AssignedKafkaCommandConsumer(
            final GatewayKafkaClientProperties kafkaClientProperties,
            final GatewayKafkaShardProperties shardProperties,
            final GatewayKafkaTopicProperties topicProperties,
            final KafkaCommandDispatcher dispatcher,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler
    ) {
        this.kafkaClientProperties = Objects.requireNonNull(kafkaClientProperties, "kafkaClientProperties is null");
        this.shardProperties = Objects.requireNonNull(shardProperties, "shardProperties is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;

        consumer = new KafkaConsumer<>(consumerProps());
        // assign() 고정: 리밸런싱으로 소유 파티션이 바뀌지 않도록 강제
        final List<TopicPartition> owned = ownedTopicPartitions();
        consumer.assign(owned);
        // Invariant: this consumer must use assign() only (no group rebalancing).
        if (!consumer.subscription().isEmpty()) {
            throw new IllegalStateException("Assigned consumer must not use subscribe()");
        }
        log.info("Assigned Kafka commands partitions: {}", owned);

        workerThread = new Thread(this, "kafka-eqp-commands-consumer");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("Assigned Kafka command consumer started. thread={}", workerThread.getName());
    }

    @Override
    public void stop() {
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
        log.info("Assigned Kafka command consumer stopped.");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void run() {
        final Duration pollTimeout = Duration.ofMillis(shardProperties.getPollTimeoutMs());
        try {
            while (running) {
                final ConsumerRecords<String, KafkaCommandMessage> records = consumer.poll(pollTimeout);
                if (records.isEmpty()) {
                    continue;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Kafka commands polled. count={}", records.count());
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
                                log.warn("Command drop (null message). topic={}, partition={}, offset={}",
                                        record.topic(), record.partition(), record.offset());
                            }
                            return;
                        }
                        // 명세: commands key는 eqpId 고정
                        if (key == null || !key.equals(message.equipmentId())) {
                            if (logSampler.shouldLogCommandDrop()) {
                                log.warn("Command drop (key mismatch). key={}, eqpId={}", key, message.equipmentId());
                            }
                            return;
                        }

                        dispatcher.dispatch(message);
                    } catch (Exception ignored) {
                    }
                });

                // drop도 정상 처리이므로 commit
                commitWithRetry();
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

    @Override
    public int getPhase() {
        return 0;
    }

    private Map<String, Object> consumerProps() {
        return kafkaClientProperties.buildConsumerProperties();
    }

    private List<TopicPartition> ownedTopicPartitions() {
        final List<TopicPartition> partitions = new ArrayList<>();
        for (Integer p : shardProperties.getOwnedPartitions()) {
            partitions.add(new TopicPartition(topicProperties.getEqpCommands(), p));
        }
        return partitions;
    }

    private void commitWithRetry() {
        final int maxRetry = shardProperties.getCommitRetryMax();
        final long backoffMs = shardProperties.getCommitRetryBackoffMs();

        int attempt = 0;
        while (true) {
            try {
                consumer.commitSync();
                if (log.isDebugEnabled()) {
                    log.debug("Kafka commit success (eqp.commands).");
                }
                return;
            } catch (Exception ex) {
                metrics.incrementKafkaCommitFail();
                if (logSampler.shouldLogCommitFail()) {
                    log.warn("Kafka commit failed (eqp.commands). attempt={}", attempt, ex);
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
            final Map<TopicPartition, Long> endOffsets = consumer.endOffsets(assignment);
            for (TopicPartition tp : assignment) {
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
