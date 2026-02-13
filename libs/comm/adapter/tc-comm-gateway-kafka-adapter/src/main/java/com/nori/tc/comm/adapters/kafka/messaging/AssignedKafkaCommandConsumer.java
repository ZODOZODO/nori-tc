package com.nori.tc.comm.adapters.kafka.messaging;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaClientProperties;
import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.adapters.kafka.messaging.contract.GatewayBusinessCommandMessage;
import com.nori.tc.comm.gateway.config.GatewayKafkaShardProperties;
import com.nori.tc.comm.gateway.metrics.GatewayLogContext;
import com.nori.tc.comm.gateway.metrics.GatewayLogSampler;
import com.nori.tc.comm.gateway.metrics.GatewayMetrics;
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
 * <p>핵심 동작은 아래와 같습니다.</p>
 * <p>1) shard 설정 기준으로 현재 인스턴스 소유 파티션만 직접 할당합니다.</p>
 * <p>2) Kafka key와 payload의 eqpId 정합성을 검증합니다.</p>
 * <p>3) 유효 메시지에 한해 business command dispatcher로 전달합니다.</p>
 */
@Component
public class AssignedKafkaCommandConsumer extends AbstractGatewayKafkaConsumer<GatewayBusinessCommandMessage> {

    private final GatewayKafkaClientProperties kafkaClientProperties;
    private final GatewayKafkaShardProperties shardProperties;
    private final GatewayKafkaTopicProperties topicProperties;
    private final GatewayCommandDispatcher dispatcher;
    private final GatewayLogSampler logSampler;

    /**
     * 고정 파티션 command consumer 의존성을 초기화합니다.
     */
    public AssignedKafkaCommandConsumer(
            final GatewayKafkaClientProperties kafkaClientProperties,
            final GatewayKafkaShardProperties shardProperties,
            final GatewayKafkaTopicProperties topicProperties,
            final GatewayCommandDispatcher dispatcher,
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
     * business command 수신 메시지 타입을 고정한 consumer properties를 생성합니다.
     */
    @Override
    protected Map<String, Object> consumerProperties() {
        return kafkaClientProperties.buildConsumerProperties(GatewayBusinessCommandMessage.class);
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
     * poll timeout(ms)를 반환합니다.
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
    protected void afterStart(final KafkaConsumer<String, GatewayBusinessCommandMessage> startedConsumer) {
        log.info("Assigned Kafka command consumer started. topic={}, partitions={}, thread={}",
                topicProperties.getEqpCommands(), assignedPartitions(), threadName());
    }

    /**
     * 수신 레코드를 검증한 뒤 dispatcher로 전달합니다.
     *
     * <p>정합성 규칙: key == data.eqpId</p>
     */
    @Override
    protected void handleRecord(final ConsumerRecord<String, GatewayBusinessCommandMessage> record) {
        final String key = record.key();
        final GatewayBusinessCommandMessage message = record.value();
        final String eqpIdForLog = extractEqpId(message, key);
        final String traceIdForLog = extractTraceId(message);

        try (GatewayLogContext ignored = GatewayLogContext.withEqpAndTraceId(eqpIdForLog, traceIdForLog)) {
            if (message == null) {
                if (logSampler.shouldLogCommandDrop()) {
                    log.warn("Command drop (null message). topic={}, partition={}, offset={}",
                            record.topic(), record.partition(), record.offset());
                }
                return;
            }

            final String messageEqpId = extractEqpId(message, null);
            if (key == null || messageEqpId == null || !key.equals(messageEqpId)) {
                if (logSampler.shouldLogCommandDrop()) {
                    log.warn("Command drop (key mismatch). key={}, eqpId={}", key, messageEqpId);
                }
                return;
            }

            if (log.isDebugEnabled()) {
                log.debug("Dispatching business command. eventType={}, interfaceType={}, eqpId={}, traceId={}, topic={}, partition={}, offset={}",
                        extractEventType(message),
                        extractInterfaceType(message),
                        messageEqpId,
                        traceIdForLog,
                        record.topic(),
                        record.partition(),
                        record.offset());
            }
            dispatcher.dispatchBusinessCommand(message);
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
     * 로그/검증에서 사용할 eqpId를 안전하게 추출합니다.
     */
    private String extractEqpId(final GatewayBusinessCommandMessage message, final String fallback) {
        if (message == null || message.data() == null || message.data().eqpId() == null) {
            return fallback;
        }
        return message.data().eqpId();
    }

    /**
     * 로그 컨텍스트 구성용 traceId를 안전하게 추출합니다.
     */
    private String extractTraceId(final GatewayBusinessCommandMessage message) {
        if (message == null || message.metadata() == null) {
            return null;
        }
        return message.metadata().traceId();
    }

    /**
     * 디버그 로그 출력용 interfaceType을 안전하게 추출합니다.
     */
    private String extractInterfaceType(final GatewayBusinessCommandMessage message) {
        if (message == null || message.data() == null) {
            return null;
        }
        return message.data().interfaceType();
    }

    /**
     * 디버그 로그 출력용 eventType을 안전하게 추출합니다.
     */
    private String extractEventType(final GatewayBusinessCommandMessage message) {
        if (message == null || message.metadata() == null) {
            return null;
        }
        return message.metadata().eventType();
    }

    /**
     * shard 설정을 starter 공통 소비 정책 계약으로 변환하는 어댑터 타입입니다.
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
