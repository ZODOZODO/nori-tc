package com.nori.tc.comm.adapters.kafka.subscribe;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaClientProperties;
import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.adapters.kafka.contract.GatewayKafkaContractSupport;
import com.nori.tc.comm.gateway.config.props.GatewayKafkaShardProperties;
import com.nori.tc.comm.gateway.config.props.GatewayObservabilityProperties;
import com.nori.tc.comm.gateway.config.props.GatewayUiTaskPolicyProperties;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogSampler;
import com.nori.tc.comm.gateway.observability.metrics.GatewayMetrics;
import com.nori.tc.messaging.kafka.contract.KafkaMessageDispatcher;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
import com.nori.tc.messaging.kafka.runtime.KafkaConsumerBindingMode;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link GatewayUiEventKafkaSubscriber}의 U10 변경(ASSIGN 전환) 회귀를 검증하는 단위 테스트입니다.
 *
 * <p>검증 목적:</p>
 * <p>1) Gateway UI consumer binding mode가 SUBSCRIBE가 아니라 ASSIGN인지</p>
 * <p>2) {@code ownedPartitions}를 기준으로 {@code tc.ui.events.gateway} 토픽 파티션을 직접 할당하는지</p>
 * <p>3) consumer 식별 이름이 ASSIGN 전환 의도에 맞게 유지되는지</p>
 */
class GatewayUiEventKafkaSubscriberTest {

    /**
     * U10 설계 규칙에 따라 UI gateway consumer가 ASSIGN 모드를 사용하는지 검증합니다.
     */
    @Test
    @DisplayName("U10: UI gateway consumer는 ASSIGN 모드를 사용한다")
    void shouldUseAssignBindingModeForUiGatewayTopic() {
        final GatewayUiEventKafkaSubscriber subscriber = newSubscriber(List.of(0, 2, 5), "tc.ui.events.gateway");

        assertEquals(KafkaConsumerBindingMode.ASSIGN, subscriber.bindingMode());
        assertEquals("ui.events.assigned", subscriber.consumerName());
    }

    /**
     * ownedPartitions 목록이 UI gateway 토픽의 assign 대상 {@link TopicPartition} 목록으로 그대로 변환되는지 검증합니다.
     *
     * <p>U10의 핵심은 command 토픽과 동일하게 UI gateway 토픽도 고정 partition(assign) 방식으로 소비하는 것입니다.</p>
     */
    @Test
    @DisplayName("U10: ownedPartitions를 tc.ui.events.gateway TopicPartition 목록으로 변환한다")
    void shouldConvertOwnedPartitionsToAssignedTopicPartitions() {
        final GatewayUiEventKafkaSubscriber subscriber = newSubscriber(List.of(1, 3, 4), "tc.ui.events.gateway");

        final List<TopicPartition> assignedPartitions = subscriber.assignedPartitions();

        assertEquals(
                List.of(
                        new TopicPartition("tc.ui.events.gateway", 1),
                        new TopicPartition("tc.ui.events.gateway", 3),
                        new TopicPartition("tc.ui.events.gateway", 4)
                ),
                assignedPartitions
        );
    }

    /**
     * 테스트용 subscriber를 생성합니다.
     *
     * <p>본 테스트는 ASSIGN 파티션 계산/바인딩 모드만 검증하므로,
     * 런타임 처리 의존성(dispatcher/contractSupport)는 Mockito mock으로 대체합니다.</p>
     *
     * @param ownedPartitions 테스트 대상 owned partition 목록
     * @param uiGatewayTopic UI gateway 토픽명
     * @return 테스트 대상 subscriber
     */
    private GatewayUiEventKafkaSubscriber newSubscriber(
            final List<Integer> ownedPartitions,
            final String uiGatewayTopic
    ) {
        // Kafka topic 매핑 설정: U1/U10 설계 기준으로 UI gateway 분리 토픽을 명시합니다.
        final GatewayKafkaTopicProperties topicProperties = new GatewayKafkaTopicProperties();
        topicProperties.setUiEvents(uiGatewayTopic);
        topicProperties.setEqpEvents("tc.eqp.events");
        topicProperties.setMesEvents("tc.mes.events");
        topicProperties.setEqpCommands("tc.eqp.commands");
        topicProperties.setMesCommands("tc.mes.commands");
        topicProperties.setUiCommands("tc.ui.commands");

        // Shard 설정: U10에서 핵심적으로 사용하는 값은 ownedPartitions이며,
        // 일부 하위 메서드 접근 대비 최소 runtime 값도 함께 채웁니다.
        final GatewayKafkaShardProperties shardProperties = new GatewayKafkaShardProperties();
        shardProperties.setOwnedPartitions(ownedPartitions);
        shardProperties.setUiPollTimeoutMs(1000L);
        shardProperties.setConsumerShutdownWaitMs(3000L);
        shardProperties.setCommitRetryMax(1);
        shardProperties.setCommitRetryBackoffMs(100L);
        shardProperties.setLagSampleIntervalMs(1000L);
        shardProperties.setAsyncRecordProcessingEnabled(false);
        shardProperties.setRecordWorkerThreads(1);
        shardProperties.setAckDrainMaxBatch(10);
        shardProperties.setMaxInFlightRecords(100);

        // UI task 정책: 실패 레코드 재시도 backoff를 포함한 최소값만 채웁니다.
        final GatewayUiTaskPolicyProperties uiTaskPolicyProperties = new GatewayUiTaskPolicyProperties();
        uiTaskPolicyProperties.setFailedRecordRetryBackoffMs(500L);

        // 로그 샘플러는 final 클래스이므로 실제 객체를 생성하여 사용합니다.
        final GatewayObservabilityProperties observabilityProperties = new GatewayObservabilityProperties();
        observabilityProperties.setCommandDropLogEvery(1);
        observabilityProperties.setBindTimeoutLogEvery(1);
        observabilityProperties.setDuplicateRejectLogEvery(1);
        observabilityProperties.setQueueOverflowLogEvery(1);
        observabilityProperties.setCommitFailLogEvery(1);
        observabilityProperties.setNotOwnerLogEvery(1);
        final GatewayLogSampler logSampler = new GatewayLogSampler(observabilityProperties);

        @SuppressWarnings("unchecked")
        final KafkaMessageDispatcher<KafkaUiTaskMessage> uiTaskDispatcher =
                (KafkaMessageDispatcher<KafkaUiTaskMessage>) Mockito.mock(KafkaMessageDispatcher.class);

        return new GatewayUiEventKafkaSubscriber(
                new GatewayKafkaClientProperties(),
                topicProperties,
                shardProperties,
                uiTaskPolicyProperties,
                uiTaskDispatcher,
                Mockito.mock(GatewayKafkaContractSupport.class),
                new GatewayMetrics(),
                logSampler
        );
    }
}

