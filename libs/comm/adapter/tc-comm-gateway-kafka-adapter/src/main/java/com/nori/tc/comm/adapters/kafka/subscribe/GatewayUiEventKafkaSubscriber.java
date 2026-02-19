package com.nori.tc.comm.adapters.kafka.messaging;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaClientProperties;
import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.gateway.config.GatewayKafkaShardProperties;
import com.nori.tc.comm.gateway.config.GatewayUiTaskPolicyProperties;
import com.nori.tc.comm.gateway.metrics.GatewayLogContext;
import com.nori.tc.comm.gateway.metrics.GatewayLogSampler;
import com.nori.tc.comm.gateway.metrics.GatewayMetrics;
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
 * {@code tc.ui.events}를 subscribe 방식으로 소비하는 UI task consumer입니다.
 *
 * <p>운영 규칙:
 * 1) UI 메시지는 반드시 REP 발행 후 commit
 * 2) 레코드 처리 실패 시 commit 하지 않고 동일 offset 재시도
 * 3) 재시도 간격은 UI task 정책값으로 제어</p>
 */
@Component
public class UiTaskKafkaEventListener extends AbstractGatewayKafkaConsumer<KafkaUiTaskMessage> {

    private final GatewayKafkaClientProperties kafkaClientProperties;
    private final GatewayKafkaTopicProperties topicProperties;
    private final GatewayKafkaShardProperties shardProperties;
    private final GatewayUiTaskPolicyProperties uiTaskPolicyProperties;
    private final KafkaMessageDispatcher<KafkaUiTaskMessage> uiTaskDispatcher;
    private final GatewayLogSampler logSampler;

    /**
     * UI task consumer 의존성을 초기화합니다.
     */
    public UiTaskKafkaEventListener(
            final GatewayKafkaClientProperties kafkaClientProperties,
            final GatewayKafkaTopicProperties topicProperties,
            final GatewayKafkaShardProperties shardProperties,
            final GatewayUiTaskPolicyProperties uiTaskPolicyProperties,
            final KafkaMessageDispatcher<KafkaUiTaskMessage> uiTaskDispatcher,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler
    ) {
        super(new GatewayShardRuntimePolicy(shardProperties), metrics, logSampler);
        this.kafkaClientProperties = Objects.requireNonNull(kafkaClientProperties, "kafkaClientProperties is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.shardProperties = Objects.requireNonNull(shardProperties, "shardProperties is null");
        this.uiTaskPolicyProperties = Objects.requireNonNull(uiTaskPolicyProperties, "uiTaskPolicyProperties is null");
        this.uiTaskDispatcher = Objects.requireNonNull(uiTaskDispatcher, "uiTaskDispatcher is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");
    }

    /**
     * UI task 직렬화 타입을 반영한 consumer properties를 구성합니다.
     */
    @Override
    protected Map<String, Object> consumerProperties() {
        return kafkaClientProperties.buildConsumerProperties(KafkaUiTaskMessage.class);
    }

    /**
     * UI task consumer는 subscribe 모드를 사용합니다.
     */
    @Override
    protected KafkaConsumerBindingMode bindingMode() {
        return KafkaConsumerBindingMode.SUBSCRIBE;
    }

    /**
     * 구독 topic을 반환합니다.
     */
    @Override
    protected List<String> subscribeTopics() {
        return List.of(topicProperties.getUiEvents());
    }

    /**
     * poll timeout(ms)을 반환합니다.
     */
    @Override
    protected Duration pollTimeout() {
        return Duration.ofMillis(shardProperties.getUiPollTimeoutMs());
    }

    /**
     * worker thread 이름입니다.
     */
    @Override
    protected String threadName() {
        return "kafka-ui-events-consumer";
    }

    /**
     * 메트릭/로그 구분용 consumer 이름입니다.
     */
    @Override
    protected String consumerName() {
        return "ui.events.subscribed";
    }

    /**
     * UI consumer는 레코드 처리 실패 시 commit을 수행하지 않습니다.
     */
    @Override
    protected boolean commitOnRecordFailure() {
        return false;
    }

    /**
     * 실패한 레코드를 동일 offset에서 재시도합니다.
     */
    @Override
    protected boolean retryFailedRecordFromCurrentOffset() {
        return true;
    }

    /**
     * 동일 레코드 재시도 backoff(ms)를 반환합니다.
     */
    @Override
    protected long failedRecordRetryBackoffMs() {
        return uiTaskPolicyProperties.getFailedRecordRetryBackoffMs();
    }

    /**
     * 시작 로그를 info로 남깁니다.
     */
    @Override
    protected void afterStart(final KafkaConsumer<String, KafkaUiTaskMessage> startedConsumer) {
        log.info("UI task consumer started. topic={}, thread={}",
                topicProperties.getUiEvents(), threadName());
    }

    /**
     * 수신 레코드를 검증 후 dispatcher로 전달합니다.
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

            if (message.metadata() == null || message.data() == null || message.data().eqpId() == null) {
                if (logSampler.shouldLogCommandDrop()) {
                    log.warn("UI task drop (invalid envelope). topic={}, partition={}, offset={}, hasMetadata={}, hasData={}",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            message.metadata() != null,
                            message.data() != null);
                }
                return;
            }

            if (key == null || !key.equals(message.data().eqpId())) {
                log.warn("UI task key mismatch detected. key={}, eqpId={}, traceId={}",
                        key, message.data().eqpId(), message.metadata().traceId());
            }

            if (log.isDebugEnabled()) {
                log.debug("Dispatching UI task. eventType={}, eqpId={}, traceId={}, topic={}, partition={}, offset={}",
                        message.metadata().eventType(),
                        message.data().eqpId(),
                        message.metadata().traceId(),
                        record.topic(),
                        record.partition(),
                        record.offset());
            }
            uiTaskDispatcher.dispatch(message);
        }
    }

    /**
     * 종료 로그를 info로 남깁니다.
     */
    @Override
    public synchronized void stop() {
        super.stop();
        log.info("UI task consumer stopped.");
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

        @Override
        public boolean asyncRecordProcessingEnabled() {
            return shardProperties.isAsyncRecordProcessingEnabled();
        }

        @Override
        public int recordWorkerThreads() {
            return shardProperties.getRecordWorkerThreads();
        }

        @Override
        public int ackDrainMaxBatch() {
            return shardProperties.getAckDrainMaxBatch();
        }

        @Override
        public int maxInFlightRecords() {
            return shardProperties.getMaxInFlightRecords();
        }
    }
}
