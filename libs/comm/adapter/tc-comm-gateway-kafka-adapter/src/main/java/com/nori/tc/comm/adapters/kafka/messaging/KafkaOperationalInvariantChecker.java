package com.nori.tc.comm.adapters.kafka.messaging;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaClientProperties;
import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.gateway.config.GatewayKafkaShardProperties;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Enforces operational invariants for Kafka at startup.
 *
 * Invariants:
 * 1) Partition count of tc.eqp.commands must match configured value.
 *    - Partition count changes are forbidden at runtime.
 * 2) Owned partitions must be within the actual partition range.
 *
 * If any invariant is violated, the application fails fast.
 */
@Component
public class KafkaOperationalInvariantChecker {

    private static final Logger log = LoggerFactory.getLogger(KafkaOperationalInvariantChecker.class);

    private final GatewayKafkaClientProperties kafkaClientProperties;
    private final GatewayKafkaShardProperties shardProperties;
    private final GatewayKafkaTopicProperties topicProperties;

    
    /**
     * 게이트웨이 Kafka 어댑터 구성 요소를 초기화합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param kafkaClientProperties 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     * @param shardProperties 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     * @param topicProperties Kafka 토픽 이름
     */
    public KafkaOperationalInvariantChecker(
            final GatewayKafkaClientProperties kafkaClientProperties,
            final GatewayKafkaShardProperties shardProperties,
            final GatewayKafkaTopicProperties topicProperties
    ) {
        this.kafkaClientProperties = Objects.requireNonNull(kafkaClientProperties, "kafkaClientProperties is null");
        this.shardProperties = Objects.requireNonNull(shardProperties, "shardProperties is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 입력/설정 유효성을 검증합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     */
    @PostConstruct
    public void verify() {
        final String topic = topicProperties.getEqpCommands();
        final Map<String, Object> adminProps = kafkaClientProperties.buildAdminProperties();

        log.info("Verifying Kafka operational invariants. topic={}, expectedPartitions={}, ownedPartitions={}",
                topic, shardProperties.getCommandsPartitionCount(), shardProperties.getOwnedPartitions());

        try (AdminClient admin = AdminClient.create(adminProps)) {
            final DescribeTopicsResult result = admin.describeTopics(List.of(topic));
            final TopicDescription description = result.allTopicNames()
                    .get(shardProperties.getAdminTimeoutSeconds(), TimeUnit.SECONDS)
                    .get(topic);

            if (description == null) {
                throw new IllegalStateException("Kafka topic not found: " + topic);
            }

            final int actualCount = description.partitions().size();
            final int expectedCount = shardProperties.getCommandsPartitionCount();

            if (actualCount != expectedCount) {
                throw new IllegalStateException(
                        "Partition count mismatch for " + topic
                                + " (expected=" + expectedCount
                                + ", actual=" + actualCount + ")"
                );
            }

            for (Integer p : shardProperties.getOwnedPartitions()) {
                if (p == null || p < 0 || p >= actualCount) {
                    throw new IllegalStateException(
                            "Owned partition out of range for " + topic + ": " + p
                                    + " (partitionCount=" + actualCount + ")"
                    );
                }
            }

            log.info("Kafka invariants OK. topic={}, partitionCount={}", topic, actualCount);
        } catch (Exception ex) {
            log.error("Kafka invariants check failed. topic={}", topic, ex);
            throw new IllegalStateException("Failed to verify Kafka invariants", ex);
        }
    }
}
