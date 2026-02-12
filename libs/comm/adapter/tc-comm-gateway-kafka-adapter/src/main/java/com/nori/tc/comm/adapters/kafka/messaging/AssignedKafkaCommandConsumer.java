package com.nori.tc.comm.adapters.kafka.messaging;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaClientProperties;
import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.gateway.config.GatewayKafkaShardProperties;
import com.nori.tc.comm.gateway.metrics.GatewayLogContext;
import com.nori.tc.comm.gateway.metrics.GatewayLogSampler;
import com.nori.tc.comm.gateway.metrics.GatewayMetrics;
import com.nori.tc.messaging.kafka.starter.contract.KafkaCommandDispatcher;
import com.nori.tc.messaging.kafka.starter.contract.KafkaCommandMessage;
import com.nori.tc.messaging.kafka.starter.runtime.KafkaConsumerBindingMode;
import com.nori.tc.messaging.kafka.starter.runtime.KafkaConsumerRuntimePolicy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@code tc.eqp.commands}를 고정 파티션(assign) 방식으로 소비하는 Consumer입니다.
 *
 * <p>핵심 동작:
 * 1) shard가 소유한 파티션만 직접 assign
 * 2) key == equipmentId 정합성 검증
 * 3) 유효한 command만 dispatcher로 전달</p>
 */
@Component
public class AssignedKafkaCommandConsumer extends AbstractGatewayKafkaConsumer<KafkaCommandMessage> {

    private final GatewayKafkaClientProperties kafkaClientProperties;
    private final GatewayKafkaShardProperties shardProperties;
    private final GatewayKafkaTopicProperties topicProperties;
    private final KafkaCommandDispatcher dispatcher;
    private final GatewayLogSampler logSampler;

    /**
     * 고정 파티션 command consumer를 초기화합니다.
     */
    public AssignedKafkaCommandConsumer(
            final GatewayKafkaClientProperties kafkaClientProperties,
            final GatewayKafkaShardProperties shardProperties,
            final GatewayKafkaTopicProperties topicProperties,
            final KafkaCommandDispatcher dispatcher,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler
    ) {
        super(new GatewayShardRuntimePolicy(shardProperties), metrics, logSampler);
        this.kafkaClientProperties = Objects.requireNonNull(kafkaClientProperties, "kafkaClientProperties is null");
        this.shardProperties = Objects.requireNonNull(shardProperties, "shardProperties is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");
    }

    /**
     * command 직렬화 타입을 반영한 consumer properties를 생성합니다.
     */
    @Override
    protected Map<String, Object> consumerProperties() {
        return kafkaClientProperties.buildConsumerProperties(KafkaCommandMessage.class);
    }

    /**
     * group subscribe 대신 assign 모드를 사용합니다.
     */
    @Override
    protected KafkaConsumerBindingMode bindingMode() {
        return KafkaConsumerBindingMode.ASSIGN;
    }

    /**
     * 현재 gateway 인스턴스가 소유한 파티션 목록을 계산합니다.
     */
    @Override
    protected List<TopicPartition> assignedPartitions() {
        final List<TopicPartition> owned = new ArrayList<>();
        for (Integer partition : shardProperties.getOwnedPartitions()) {
            owned.add(new TopicPartition(topicProperties.getEqpCommands(), partition));
        }
        return owned;
    }

    /**
     * poll timeout(ms)을 반환합니다.
     */
    @Override
    protected Duration pollTimeout() {
        return Duration.ofMillis(shardProperties.getPollTimeoutMs());
    }

    /**
     * worker thread 이름입니다.
     */
    @Override
    protected String threadName() {
        return "kafka-eqp-commands-consumer";
    }

    /**
     * 메트릭/로그 구분용 consumer 이름입니다.
     */
    @Override
    protected String consumerName() {
        return "eqp.commands.assigned";
    }

    /**
     * 시작 완료 시 topic/partition 정보를 info 로그로 남깁니다.
     */
    @Override
    protected void afterStart(final KafkaConsumer<String, KafkaCommandMessage> startedConsumer) {
        log.info("Assigned Kafka command consumer started. topic={}, partitions={}, thread={}",
                topicProperties.getEqpCommands(), assignedPartitions(), threadName());
    }

    /**
     * 수신 레코드를 검증 후 command dispatcher로 전달합니다.
     *
     * <p>정합성 규칙:
     * key == message.equipmentId 이어야 합니다.</p>
     */
    @Override
    protected void handleRecord(final ConsumerRecord<String, KafkaCommandMessage> record) {
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

            if (key == null || !key.equals(message.equipmentId())) {
                if (logSampler.shouldLogCommandDrop()) {
                    log.warn("Command drop (key mismatch). key={}, eqpId={}", key, message.equipmentId());
                }
                return;
            }

            if (log.isDebugEnabled()) {
                log.debug("Dispatching command. eqpId={}, traceId={}, topic={}, partition={}, offset={}",
                        message.equipmentId(),
                        message.traceId(),
                        record.topic(),
                        record.partition(),
                        record.offset());
            }
            dispatcher.dispatch(message);
        }
    }

    /**
     * 종료 로그를 info로 남깁니다.
     */
    @Override
    public synchronized void stop() {
        super.stop();
        log.info("Assigned Kafka command consumer stopped.");
    }

    /**
     * shard 설정을 starter 공통 정책 계약으로 변환하는 어댑터입니다.
     */
    private static final class GatewayShardRuntimePolicy implements KafkaConsumerRuntimePolicy {

        private final GatewayKafkaShardProperties shardProperties;

        private GatewayShardRuntimePolicy(final GatewayKafkaShardProperties shardProperties) {
            this.shardProperties = Objects.requireNonNull(shardProperties, "shardProperties is null");
        }

        @Override
        public long shutdownWaitMs() {
            return shardProperties.getConsumerShutdownWaitMs();
        }

        @Override
        public int commitRetryMax() {
            return shardProperties.getCommitRetryMax();
        }

        @Override
        public long commitRetryBackoffMs() {
            return shardProperties.getCommitRetryBackoffMs();
        }

        @Override
        public long lagSampleIntervalMs() {
            return shardProperties.getLagSampleIntervalMs();
        }
    }
}
