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
import com.nori.tc.messaging.kafka.starter.runtime.AbstractKafkaConsumerLifecycle;
import com.nori.tc.messaging.kafka.starter.runtime.KafkaConsumerBindingMode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@code tc.ui.events}를 subscribe 방식으로 수신하는 UI task consumer입니다.
 *
 * <p>정책:</p>
 * <p>- UI 요청은 반드시 REP 발행 후 커밋</p>
 * <p>- REP 발행 실패 시 레코드를 커밋하지 않고 동일 offset 재시도</p>
 */
@Component
public class UiTaskKafkaEventListener extends AbstractKafkaConsumerLifecycle<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(UiTaskKafkaEventListener.class);

    private final GatewayKafkaClientProperties kafkaClientProperties;
    private final GatewayKafkaTopicProperties topicProperties;
    private final GatewayKafkaShardProperties shardProperties;
    private final GatewayUiTaskPolicyProperties uiTaskPolicyProperties;
    private final KafkaMessageDispatcher<KafkaUiTaskMessage> uiTaskDispatcher;
    private final GatewayMetrics metrics;
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
        this.kafkaClientProperties = Objects.requireNonNull(kafkaClientProperties, "kafkaClientProperties is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.shardProperties = Objects.requireNonNull(shardProperties, "shardProperties is null");
        this.uiTaskPolicyProperties = Objects.requireNonNull(uiTaskPolicyProperties, "uiTaskPolicyProperties is null");
        this.uiTaskDispatcher = Objects.requireNonNull(uiTaskDispatcher, "uiTaskDispatcher is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");
    }

    /**
     * UI task 직렬화 타입이 반영된 consumer 프로퍼티를 구성합니다.
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
     * 구독 토픽을 반환합니다.
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
     * worker thread 이름을 반환합니다.
     */
    @Override
    protected String threadName() {
        return "kafka-ui-events-consumer";
    }

    @Override
    protected long shutdownWaitMs() {
        return shardProperties.getConsumerShutdownWaitMs();
    }

    @Override
    protected int commitRetryMax() {
        return shardProperties.getCommitRetryMax();
    }

    @Override
    protected long commitRetryBackoffMs() {
        return shardProperties.getCommitRetryBackoffMs();
    }

    @Override
    protected long lagSampleIntervalMs() {
        return shardProperties.getLagSampleIntervalMs();
    }

    @Override
    protected String consumerName() {
        return "ui.events.subscribed";
    }

    /**
     * UI consumer는 레코드 실패 시 커밋을 진행하지 않습니다.
     */
    @Override
    protected boolean commitOnRecordFailure() {
        return false;
    }

    /**
     * UI consumer는 실패 레코드를 같은 offset에서 재시도합니다.
     */
    @Override
    protected boolean retryFailedRecordFromCurrentOffset() {
        return true;
    }

    /**
     * 동일 레코드 재시도 전 backoff(ms)를 반환합니다.
     */
    @Override
    protected long failedRecordRetryBackoffMs() {
        return uiTaskPolicyProperties.getFailedRecordRetryBackoffMs();
    }

    /**
     * 시작 로그를 출력합니다.
     */
    @Override
    protected void afterStart(final KafkaConsumer<String, KafkaUiTaskMessage> startedConsumer) {
        log.info("UI task consumer started. topic={}, thread={}",
                topicProperties.getUiEvents(), threadName());
    }

    /**
     * 단건 레코드를 검증 후 dispatcher로 전달합니다.
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

            // key와 eqpId가 다르더라도 REP 보장을 위해 처리를 진행합니다.
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
     * commit 실패 카운트 및 로그를 기록합니다.
     */
    @Override
    protected void onCommitFail(final Exception ex, final int attempt) {
        metrics.incrementKafkaCommitFail();
        if (logSampler.shouldLogCommitFail()) {
            log.warn("Kafka commit failed (ui.events). attempt={}", attempt, ex);
        }
    }

    @Override
    protected void onLagSample(final TopicPartition topicPartition, final long lag) {
        metrics.recordConsumerLag(topicPartition.topic(), topicPartition.partition(), lag);
    }

    /**
     * 종료 시 상태 로그를 남깁니다.
     */
    @Override
    public synchronized void stop() {
        super.stop();
        log.info("UI task consumer stopped.");
    }
}
