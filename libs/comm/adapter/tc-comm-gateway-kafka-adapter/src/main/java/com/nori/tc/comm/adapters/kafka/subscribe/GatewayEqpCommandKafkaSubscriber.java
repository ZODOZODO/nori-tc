package com.nori.tc.comm.adapters.kafka.subscribe;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaClientProperties;
import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.adapters.kafka.contract.GatewayBusinessCommandMessage;
import com.nori.tc.comm.adapters.kafka.contract.GatewayKafkaContractSupport;
import com.nori.tc.comm.gateway.config.props.GatewayKafkaShardProperties;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogContext;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogSampler;
import com.nori.tc.comm.gateway.observability.logging.GatewayObservationLogger;
import com.nori.tc.comm.gateway.observability.metrics.GatewayMetrics;
import com.nori.tc.messaging.domain.kafka.contract.TcCommonKafkaMetadata;
import com.nori.tc.messaging.kafka.runtime.KafkaConsumerBindingMode;
import com.nori.tc.messaging.kafka.runtime.KafkaConsumerRuntimePolicy;
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
 * {@code tc.eqp.commands}를 고정 파티션(assign) 방식으로 구독하는 Consumer입니다.
 *
 * <p>처리 순서:
 * 1) 현재 인스턴스가 소유한 파티션만 직접 할당
 * 2) envelope/metadata/Kafka key 계약 검증
 * 3) 검증된 메시지를 {@link GatewayCommandDispatcher}로 전달</p>
 */
@Component
public class GatewayEqpCommandKafkaSubscriber extends AbstractGatewayKafkaSubscriber<GatewayBusinessCommandMessage> {

    private final GatewayKafkaClientProperties kafkaClientProperties;
    private final GatewayKafkaShardProperties shardProperties;
    private final GatewayKafkaTopicProperties topicProperties;
    private final GatewayCommandDispatcher dispatcher;
    private final GatewayKafkaContractSupport contractSupport;
    private final GatewayLogSampler logSampler;

    /**
     * 고정 파티션 command consumer 의존성을 초기화합니다.
     *
     * @param kafkaClientProperties Kafka consumer 설정
     * @param shardProperties shard/partition 정책
     * @param topicProperties topic 매핑 설정
     * @param dispatcher command 처리 디스패처
     * @param contractSupport Kafka 계약 검증 지원기
     * @param metrics gateway 메트릭
     * @param logSampler 샘플링 로그 정책
     */
    public GatewayEqpCommandKafkaSubscriber(
            final GatewayKafkaClientProperties kafkaClientProperties,
            final GatewayKafkaShardProperties shardProperties,
            final GatewayKafkaTopicProperties topicProperties,
            final GatewayCommandDispatcher dispatcher,
            final GatewayKafkaContractSupport contractSupport,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler
    ) {
        super(new GatewayShardRuntimePolicy(shardProperties), metrics, logSampler);
        this.kafkaClientProperties = Objects.requireNonNull(kafkaClientProperties, "kafkaClientProperties is null");
        this.shardProperties = Objects.requireNonNull(shardProperties, "shardProperties is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher is null");
        this.contractSupport = Objects.requireNonNull(contractSupport, "contractSupport is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");
    }

    /**
     * command 역직렬화 타입을 포함한 consumer properties를 반환합니다.
     *
     * @return Kafka consumer 설정 맵
     */
    @Override
    protected Map<String, Object> consumerProperties() {
        return kafkaClientProperties.buildConsumerProperties(GatewayBusinessCommandMessage.class);
    }

    /**
     * consumer binding 모드를 assign으로 고정합니다.
     *
     * @return assign 모드
     */
    @Override
    protected KafkaConsumerBindingMode bindingMode() {
        return KafkaConsumerBindingMode.ASSIGN;
    }

    /**
     * 현재 인스턴스가 처리할 파티션 목록을 계산합니다.
     *
     * @return assign 대상 파티션 목록
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
     * poll timeout 값을 반환합니다.
     *
     * @return poll timeout
     */
    @Override
    protected Duration pollTimeout() {
        return Duration.ofMillis(shardProperties.getPollTimeoutMs());
    }

    /**
     * consumer worker 스레드 이름을 반환합니다.
     *
     * @return worker 스레드 이름
     */
    @Override
    protected String threadName() {
        return "kafka-eqp-commands-consumer";
    }

    /**
     * 메트릭/로그 구분용 consumer 이름을 반환합니다.
     *
     * @return consumer 식별 이름
     */
    @Override
    protected String consumerName() {
        return "eqp.commands.assigned";
    }

    /**
     * consumer 시작 직후 현재 구독 상태를 info 로그로 남깁니다.
     *
     * @param startedConsumer 시작된 consumer 인스턴스
     */
    @Override
    protected void afterStart(final KafkaConsumer<String, GatewayBusinessCommandMessage> startedConsumer) {
        log.info("Assigned Kafka command consumer started. topic={}, partitions={}, thread={}",
                topicProperties.getEqpCommands(), assignedPartitions(), threadName());
    }

    /**
     * 수신 레코드를 계약 검증 후 dispatcher로 전달합니다.
     *
     * <p>검증 실패 메시지는 운영 안정성을 위해 drop 처리하고 다음 레코드로 진행합니다.</p>
     *
     * @param record Kafka 수신 레코드
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
                    log.warn("GW_CMD_KAFKA_IN_REJECTED. reason=NULL_MESSAGE, topic={}, partition={}, offset={}",
                            record.topic(), record.partition(), record.offset());
                }
                return;
            }

            final TcCommonKafkaMetadata metadata;
            try {
                metadata = contractSupport.validateGatewayBusinessCommandRecord(
                        topicProperties.getEqpCommands(),
                        key,
                        message
                );
            } catch (IllegalArgumentException ex) {
                if (logSampler.shouldLogCommandDrop()) {
                    log.warn("GW_CMD_KAFKA_IN_REJECTED. reason=CONTRACT_VALIDATION_FAILED, topic={}, partition={}, offset={}, key={}",
                            record.topic(), record.partition(), record.offset(), key, ex);
                }
                return;
            }

            final String messageEqpId = message.data().eqpId();
            GatewayObservationLogger.logCmdKafkaInboundAccepted(
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    messageEqpId,
                    metadata.traceId(),
                    metadata.eventType()
            );
            dispatcher.dispatchBusinessCommand(
                    message,
                    record.topic(),
                    record.partition(),
                    record.offset()
            );
        }
    }

    /**
     * consumer 종료 시 상태를 info 로그로 남깁니다.
     */
    @Override
    public synchronized void stop() {
        super.stop();
        log.info("Assigned Kafka command consumer stopped.");
    }

    /**
     * 로그 컨텍스트용 eqpId를 안전하게 추출합니다.
     *
     * @param message command payload
     * @param fallback 기본값
     * @return 추출된 eqpId
     */
    private String extractEqpId(final GatewayBusinessCommandMessage message, final String fallback) {
        if (message == null || message.data() == null || message.data().eqpId() == null) {
            return fallback;
        }
        return message.data().eqpId();
    }

    /**
     * 로그 컨텍스트용 traceId를 안전하게 추출합니다.
     *
     * @param message command payload
     * @return 추출된 traceId
     */
    private String extractTraceId(final GatewayBusinessCommandMessage message) {
        if (message == null || message.metadata() == null) {
            return null;
        }
        return message.metadata().traceId();
    }

    /**
     * shard 설정을 공통 consumer 정책 인터페이스로 변환하는 어댑터입니다.
     */
    private static final class GatewayShardRuntimePolicy implements KafkaConsumerRuntimePolicy {

        private final GatewayKafkaShardProperties shardProperties;

        /**
         * GatewayShardRuntimePolicy 생성자를 초기화합니다.
         *
         * @param shardProperties 입력 값
         */

        private GatewayShardRuntimePolicy(final GatewayKafkaShardProperties shardProperties) {
            this.shardProperties = Objects.requireNonNull(shardProperties, "shardProperties is null");
        }

        /**
         * shutdownWaitMs 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        @Override
        public long shutdownWaitMs() {
            return shardProperties.getConsumerShutdownWaitMs();
        }

        /**
         * commitRetryMax 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        @Override
        public int commitRetryMax() {
            return shardProperties.getCommitRetryMax();
        }

        /**
         * commitRetryBackoffMs 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        @Override
        public long commitRetryBackoffMs() {
            return shardProperties.getCommitRetryBackoffMs();
        }

        /**
         * lagSampleIntervalMs 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        @Override
        public long lagSampleIntervalMs() {
            return shardProperties.getLagSampleIntervalMs();
        }

        /**
         * asyncRecordProcessingEnabled 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        @Override
        public boolean asyncRecordProcessingEnabled() {
            return shardProperties.isAsyncRecordProcessingEnabled();
        }

        /**
         * recordWorkerThreads 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        @Override
        public int recordWorkerThreads() {
            return shardProperties.getRecordWorkerThreads();
        }

        /**
         * ackDrainMaxBatch 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        @Override
        public int ackDrainMaxBatch() {
            return shardProperties.getAckDrainMaxBatch();
        }

        /**
         * maxInFlightRecords 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        @Override
        public int maxInFlightRecords() {
            return shardProperties.getMaxInFlightRecords();
        }
    }
}
