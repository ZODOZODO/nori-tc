package com.nori.tc.apps.commgateway.messaging;

import com.nori.tc.apps.commgateway.config.GatewayKafkaClientProperties;
import com.nori.tc.apps.commgateway.config.GatewayKafkaTopicProperties;
import com.nori.tc.apps.commgateway.metrics.GatewayLogContext;
import com.nori.tc.apps.commgateway.metrics.GatewayLogSampler;
import com.nori.tc.apps.commgateway.metrics.GatewayMetrics;
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
    private final com.nori.tc.apps.commgateway.config.GatewayKafkaShardProperties shardProperties;
    private final KafkaCommandDispatcher dispatcher;
    private final GatewayMetrics metrics;
    private final GatewayLogSampler logSampler;

    private KafkaConsumer<String, KafkaCommandMessage> consumer;
    private Thread workerThread;
    private volatile boolean running = false;
    private volatile long lastLagSampleAt = 0L;

    public KafkaCommandListener(
            final GatewayKafkaClientProperties kafkaClientProperties,
            final GatewayKafkaTopicProperties topicProperties,
            final com.nori.tc.apps.commgateway.config.GatewayKafkaShardProperties shardProperties,
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

    @Override
    public void start() {
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
        log.info("Kafka UI command consumer stopped.");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    @Override
    public void run() {
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

    private Map<String, Object> consumerProps() {
        return kafkaClientProperties.buildConsumerProperties();
    }

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
