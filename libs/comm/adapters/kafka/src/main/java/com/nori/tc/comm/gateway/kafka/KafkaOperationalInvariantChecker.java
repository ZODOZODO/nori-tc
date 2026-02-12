package com.nori.tc.apps.commgateway.kafka;

import com.nori.tc.apps.commgateway.config.GatewayKafkaClientProperties;
import com.nori.tc.apps.commgateway.config.GatewayKafkaShardProperties;
import com.nori.tc.apps.commgateway.config.GatewayKafkaTopicProperties;
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

    public KafkaOperationalInvariantChecker(
            final GatewayKafkaClientProperties kafkaClientProperties,
            final GatewayKafkaShardProperties shardProperties,
            final GatewayKafkaTopicProperties topicProperties
    ) {
        this.kafkaClientProperties = Objects.requireNonNull(kafkaClientProperties, "kafkaClientProperties is null");
        this.shardProperties = Objects.requireNonNull(shardProperties, "shardProperties is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
    }

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
