package com.nori.tc.comm.adapters.kafka.subscribe;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaClientProperties;
import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.adapters.kafka.contract.GatewayKafkaContractSupport;
import com.nori.tc.comm.gateway.config.props.GatewayKafkaShardProperties;
import com.nori.tc.comm.gateway.config.props.GatewayUiTaskPolicyProperties;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogContext;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogSampler;
import com.nori.tc.comm.gateway.observability.logging.GatewayObservationLogger;
import com.nori.tc.comm.gateway.observability.metrics.GatewayMetrics;
import com.nori.tc.messaging.domain.kafka.contract.TcCommonKafkaMetadata;
import com.nori.tc.messaging.kafka.contract.KafkaMessageDispatcher;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
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
 * {@code tc.ui.events.gateway}를 고정 파티션(assign) 방식으로 구독하는 UI Task consumer입니다.
 *
 * <p>운영 정책:
 * 1) task 처리 실패 시 commit하지 않고 동일 offset을 재시도
 * 2) envelope/key 계약 검증 실패 메시지는 drop 처리
 * 3) 현재 인스턴스가 소유한 partition만 직접 할당하여 소비
 * 4) 유효 메시지는 공통 UI dispatcher로 전달</p>
 *
 * <p>U10 변경 규칙:</p>
 * <p>- 기존 SUBSCRIBE(consumer group rebalance) 방식에서 ASSIGN(고정 partition 할당) 방식으로 전환합니다.</p>
 * <p>- {@code tc.eqp.commands}와 동일하게 {@code ownedPartitions}를 기준으로 UI gateway 토픽을 소비하여
 *   고정 partition 라우팅 정책과 일관성을 맞춥니다.</p>
 */
@Component
public class GatewayUiEventKafkaSubscriber extends AbstractGatewayKafkaSubscriber<KafkaUiTaskMessage> {

    private final GatewayKafkaClientProperties kafkaClientProperties;
    private final GatewayKafkaTopicProperties topicProperties;
    private final GatewayKafkaShardProperties shardProperties;
    private final GatewayUiTaskPolicyProperties uiTaskPolicyProperties;
    private final KafkaMessageDispatcher<KafkaUiTaskMessage> uiTaskDispatcher;
    private final GatewayKafkaContractSupport contractSupport;
    private final GatewayLogSampler logSampler;

    /**
     * UI task consumer 의존성을 초기화합니다.
     *
     * @param kafkaClientProperties Kafka consumer 설정
     * @param topicProperties topic 매핑 설정
     * @param shardProperties shard/runtime 정책
     * @param uiTaskPolicyProperties UI task 재시도 정책
     * @param uiTaskDispatcher 공통 UI task 디스패처
     * @param contractSupport Kafka 계약 검증 지원기
     * @param metrics gateway 메트릭
     * @param logSampler 샘플링 로그 정책
     */
    public GatewayUiEventKafkaSubscriber(
            final GatewayKafkaClientProperties kafkaClientProperties,
            final GatewayKafkaTopicProperties topicProperties,
            final GatewayKafkaShardProperties shardProperties,
            final GatewayUiTaskPolicyProperties uiTaskPolicyProperties,
            final KafkaMessageDispatcher<KafkaUiTaskMessage> uiTaskDispatcher,
            final GatewayKafkaContractSupport contractSupport,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler
    ) {
        super(new GatewayShardRuntimePolicy(shardProperties), metrics, logSampler);
        this.kafkaClientProperties = Objects.requireNonNull(kafkaClientProperties, "kafkaClientProperties is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.shardProperties = Objects.requireNonNull(shardProperties, "shardProperties is null");
        this.uiTaskPolicyProperties = Objects.requireNonNull(uiTaskPolicyProperties, "uiTaskPolicyProperties is null");
        this.uiTaskDispatcher = Objects.requireNonNull(uiTaskDispatcher, "uiTaskDispatcher is null");
        this.contractSupport = Objects.requireNonNull(contractSupport, "contractSupport is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");
    }

    /**
     * UI task 역직렬화 타입을 반영한 consumer properties를 반환합니다.
     *
     * @return Kafka consumer 설정 맵
     */
    @Override
    protected Map<String, Object> consumerProperties() {
        return kafkaClientProperties.buildConsumerProperties(KafkaUiTaskMessage.class);
    }

    /**
     * consumer binding 모드를 assign으로 지정합니다.
     *
     * @return assign 모드
     */
    @Override
    protected KafkaConsumerBindingMode bindingMode() {
        return KafkaConsumerBindingMode.ASSIGN;
    }

    /**
     * 현재 인스턴스가 처리할 UI gateway 토픽 파티션 목록을 계산합니다.
     *
     * <p>U10부터 gateway UI 이벤트도 command 토픽과 동일하게 고정 partition(assign) 방식으로 소비합니다.</p>
     * <p>할당 대상 파티션은 {@code GatewayKafkaShardProperties.ownedPartitions}를 그대로 사용합니다.</p>
     *
     * @return assign 대상 topic partition 목록
     */
    @Override
    protected List<TopicPartition> assignedPartitions() {
        final List<TopicPartition> owned = new ArrayList<>();
        for (Integer partition : shardProperties.getOwnedPartitions()) {
            owned.add(new TopicPartition(topicProperties.getUiEvents(), partition));
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
        return Duration.ofMillis(shardProperties.getUiPollTimeoutMs());
    }

    /**
     * consumer worker 스레드 이름을 반환합니다.
     *
     * @return worker 스레드 이름
     */
    @Override
    protected String threadName() {
        return "kafka-ui-events-consumer";
    }

    /**
     * 메트릭/로그 구분용 consumer 이름을 반환합니다.
     *
     * @return consumer 식별 이름
     */
    @Override
    protected String consumerName() {
        return "ui.events.assigned";
    }

    /**
     * 처리 실패 레코드는 commit하지 않고 재처리합니다.
     *
     * @return 항상 false
     */
    @Override
    protected boolean commitOnRecordFailure() {
        return false;
    }

    /**
     * 실패 레코드는 동일 offset에서 재시도합니다.
     *
     * @return 항상 true
     */
    @Override
    protected boolean retryFailedRecordFromCurrentOffset() {
        return true;
    }

    /**
     * 실패 재시도 backoff(ms)를 반환합니다.
     *
     * @return retry backoff(ms)
     */
    @Override
    protected long failedRecordRetryBackoffMs() {
        return uiTaskPolicyProperties.getFailedRecordRetryBackoffMs();
    }

    /**
     * consumer 시작 시 운영 로그를 남깁니다.
     *
     * @param startedConsumer 시작된 consumer 인스턴스
     */
    @Override
    protected void afterStart(final KafkaConsumer<String, KafkaUiTaskMessage> startedConsumer) {
        log.info("Assigned UI task consumer started. topic={}, partitions={}, thread={}",
                topicProperties.getUiEvents(),
                assignedPartitions(),
                threadName());
    }

    /**
     * UI task 레코드를 검증 후 dispatcher로 전달합니다.
     *
     * @param record Kafka 수신 레코드
     */
    @Override
    protected void handleRecord(final ConsumerRecord<String, KafkaUiTaskMessage> record) {
        final String key = record.key();
        final KafkaUiTaskMessage message = record.value();
        final String eqpIdForLog = (message != null && message.data() != null) ? message.data().eqpId() : key;
        final String traceIdForLog = (message != null && message.metadata() != null) ? message.metadata().traceId() : null;

        try (GatewayLogContext ignored = GatewayLogContext.withEqpAndTraceId(eqpIdForLog, traceIdForLog)) {
            if (message == null) {
                if (logSampler.shouldLogCommandDrop()) {
                    log.warn("GW_UI_KAFKA_IN_REJECTED. reason=NULL_MESSAGE, topic={}, partition={}, offset={}",
                            record.topic(), record.partition(), record.offset());
                }
                return;
            }

            final TcCommonKafkaMetadata metadata;
            try {
                metadata = contractSupport.validateUiTaskEventRecord(
                        topicProperties.getUiEvents(),
                        key,
                        message
                );
            } catch (IllegalArgumentException ex) {
                if (logSampler.shouldLogCommandDrop()) {
                    log.warn("GW_UI_KAFKA_IN_REJECTED. reason=CONTRACT_VALIDATION_FAILED, topic={}, partition={}, offset={}, key={}",
                            record.topic(), record.partition(), record.offset(), key, ex);
                }
                return;
            }

            GatewayObservationLogger.logUiKafkaInboundAccepted(
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    message.data().eqpId(),
                    metadata.traceId(),
                    metadata.eventType()
            );
            uiTaskDispatcher.dispatch(message);
        }
    }

    /**
     * consumer 종료 시 상태 로그를 남깁니다.
     */
    @Override
    public synchronized void stop() {
        super.stop();
        log.info("UI task consumer stopped.");
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
