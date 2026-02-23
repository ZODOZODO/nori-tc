package com.nori.tc.comm.adapters.kafka.subscribe;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaClientProperties;
import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.adapters.kafka.contract.GatewayKafkaContractSupport;
import com.nori.tc.comm.gateway.config.props.GatewayKafkaShardProperties;
import com.nori.tc.comm.gateway.config.props.GatewayUiTaskPolicyProperties;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogContext;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogSampler;
import com.nori.tc.comm.gateway.observability.metrics.GatewayMetrics;
import com.nori.tc.messaging.domain.kafka.contract.TcCommonKafkaMetadata;
import com.nori.tc.messaging.kafka.starter.contract.KafkaMessageDispatcher;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import com.nori.tc.messaging.kafka.starter.runtime.KafkaConsumerBindingMode;
import com.nori.tc.messaging.kafka.starter.runtime.KafkaConsumerRuntimePolicy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@code tc.ui.events}를 subscribe 방식으로 구독하는 UI Task consumer입니다.
 *
 * <p>운영 정책:
 * 1) task 처리 실패 시 commit하지 않고 동일 offset을 재시도
 * 2) envelope/key 계약 검증 실패 메시지는 drop 처리
 * 3) 유효 메시지는 공통 UI dispatcher로 전달</p>
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
     * consumer binding 모드를 subscribe로 지정합니다.
     *
     * @return subscribe 모드
     */
    @Override
    protected KafkaConsumerBindingMode bindingMode() {
        return KafkaConsumerBindingMode.SUBSCRIBE;
    }

    /**
     * 구독 대상 topic을 반환합니다.
     *
     * @return 구독 topic 목록
     */
    @Override
    protected List<String> subscribeTopics() {
        return List.of(topicProperties.getUiEvents());
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
        return "ui.events.subscribed";
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
        log.info("UI task consumer started. topic={}, thread={}",
                topicProperties.getUiEvents(), threadName());
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
                    log.warn("UI task drop (null message). topic={}, partition={}, offset={}",
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
                    log.warn("UI task drop (contract validation failed). topic={}, partition={}, offset={}, key={}",
                            record.topic(), record.partition(), record.offset(), key, ex);
                }
                return;
            }

            if (log.isDebugEnabled()) {
                log.debug("Dispatching UI task. eventType={}, eqpId={}, traceId={}, topic={}, partition={}, offset={}",
                        metadata.eventType(),
                        message.data().eqpId(),
                        metadata.traceId(),
                        record.topic(),
                        record.partition(),
                        record.offset());
            }
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
