package com.nori.tc.comm.adapters.kafka.messaging;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaClientProperties;
import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.gateway.config.GatewayKafkaShardProperties;
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
 * {@code tc.ui.events}를 subscribe 방식으로 수신하는 UI task Consumer입니다.
 *
 * <p>UI 백엔드에서 내려주는 장비 런타임 제어 요청을 읽어
 * 전용 dispatcher로 위임합니다.</p>
 */
@Component
public class UiTaskKafkaEventListener extends AbstractKafkaConsumerLifecycle<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(UiTaskKafkaEventListener.class);

    private final GatewayKafkaClientProperties kafkaClientProperties;
    private final GatewayKafkaTopicProperties topicProperties;
    private final GatewayKafkaShardProperties shardProperties;
    private final KafkaMessageDispatcher<KafkaUiTaskMessage> uiTaskDispatcher;
    private final GatewayMetrics metrics;
    private final GatewayLogSampler logSampler;

    public UiTaskKafkaEventListener(
            final GatewayKafkaClientProperties kafkaClientProperties,
            final GatewayKafkaTopicProperties topicProperties,
            final GatewayKafkaShardProperties shardProperties,
            final KafkaMessageDispatcher<KafkaUiTaskMessage> uiTaskDispatcher,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler
    ) {
        this.kafkaClientProperties = Objects.requireNonNull(kafkaClientProperties, "kafkaClientProperties is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.shardProperties = Objects.requireNonNull(shardProperties, "shardProperties is null");
        this.uiTaskDispatcher = Objects.requireNonNull(uiTaskDispatcher, "uiTaskDispatcher is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");
    }

    /**
     * UI task 역직렬화 타입이 반영된 consumer 프로퍼티를 구성합니다.
     */
    @Override
    protected Map<String, Object> consumerProperties() {
        return kafkaClientProperties.buildConsumerProperties(KafkaUiTaskMessage.class);
    }

    /**
     * UI task consumer는 구독 기반 리밸런싱 모드를 사용합니다.
     */
    @Override
    protected KafkaConsumerBindingMode bindingMode() {
        return KafkaConsumerBindingMode.SUBSCRIBE;
    }

    /**
     * 구독할 UI 이벤트 토픽을 반환합니다.
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
     * 시작 시 topic/thread 정보를 info 로그로 출력합니다.
     */
    @Override
    protected void afterStart(final KafkaConsumer<String, KafkaUiTaskMessage> startedConsumer) {
        log.info("UI task consumer started. topic={}, thread={}",
                topicProperties.getUiEvents(), threadName());
    }

    /**
     * 수신 레코드를 검증 후 UI task dispatcher로 전달합니다.
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

            if (key == null || !key.equals(message.data().eqpId())) {
                if (logSampler.shouldLogCommandDrop()) {
                    log.warn("UI task drop (key mismatch). key={}, eqpId={}", key, message.data().eqpId());
                }
                return;
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
     * commit 실패 카운터/로그를 기록합니다.
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
     * 종료 시 상위 공통 stop 호출 후 상태 로그를 남깁니다.
     */
    @Override
    public synchronized void stop() {
        super.stop();
        log.info("UI task consumer stopped.");
    }
}
