package com.nori.tc.comm.adapters.kafka.messaging;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaClientProperties;
import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.gateway.config.GatewayKafkaShardProperties;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.TopicDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Gateway Kafka 운영 불변식(startup invariant) 검증기입니다.
 *
 * <p>기동 직후 아래 조건을 반드시 만족해야만 앱이 정상 실행됩니다.</p>
 * <p>1) Gateway가 사용하는 필수 토픽이 모두 존재해야 합니다.</p>
 * <p>2) 모든 필수 토픽의 파티션 수는 1 이상이어야 합니다.</p>
 * <p>3) tc.eqp.commands 파티션 수는 설정값(commandsPartitionCount)과 정확히 일치해야 합니다.</p>
 * <p>4) ownedPartitions는 실제 파티션 범위를 벗어나면 안 됩니다.</p>
 */
@Component
public class KafkaOperationalInvariantChecker {

    private static final Logger log = LoggerFactory.getLogger(KafkaOperationalInvariantChecker.class);

    private final GatewayKafkaClientProperties kafkaClientProperties;
    private final GatewayKafkaShardProperties shardProperties;
    private final GatewayKafkaTopicProperties topicProperties;

    /**
     * 불변식 검증에 필요한 의존성을 초기화합니다.
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
     * 기동 시점에 Kafka 토픽/파티션 불변식을 검증합니다.
     */
    @PostConstruct
    public void verify() {
        final List<String> requiredTopics = buildRequiredTopics();
        final Map<String, Object> adminProps = kafkaClientProperties.buildAdminProperties();
        final String commandTopic = topicProperties.getEqpCommands();

        log.info("Verifying Kafka startup invariants. requiredTopics={}, commandTopic={}, expectedCommandPartitions={}, ownedPartitions={}",
                requiredTopics,
                commandTopic,
                shardProperties.getCommandsPartitionCount(),
                shardProperties.getOwnedPartitions());

        try (AdminClient admin = AdminClient.create(adminProps)) {
            final Map<String, TopicDescription> topicDescriptions = admin.describeTopics(requiredTopics)
                    .allTopicNames()
                    .get(shardProperties.getAdminTimeoutSeconds(), TimeUnit.SECONDS);

            for (String topic : requiredTopics) {
                final TopicDescription topicDescription = topicDescriptions.get(topic);
                if (topicDescription == null) {
                    throw new IllegalStateException("Kafka topic not found: " + topic);
                }

                final int partitionCount = topicDescription.partitions().size();
                if (partitionCount <= 0) {
                    throw new IllegalStateException("Kafka topic must have at least one partition: " + topic);
                }

                if (topic.equals(commandTopic)) {
                    verifyCommandTopicPartitionInvariant(topic, partitionCount);
                }
            }

            log.info("Kafka startup invariants verified. topics={}, commandTopic={}, commandPartitions={}",
                    requiredTopics,
                    commandTopic,
                    topicDescriptions.get(commandTopic).partitions().size());
            if (log.isDebugEnabled()) {
                log.debug("Kafka topic descriptions={}", topicDescriptions);
            }
        } catch (Exception ex) {
            log.error("Kafka startup invariants verification failed. requiredTopics={}", requiredTopics, ex);
            throw new IllegalStateException("Failed to verify Kafka startup invariants", ex);
        }
    }

    /**
     * Gateway 운영에 필수인 토픽 목록을 중복 없이 구성합니다.
     */
    private List<String> buildRequiredTopics() {
        final Set<String> unique = new LinkedHashSet<>();
        unique.add(topicProperties.getEqpEvents());
        unique.add(topicProperties.getEqpCommands());
        unique.add(topicProperties.getUiEvents());
        unique.add(topicProperties.getUiCommands());
        if (unique.size() != 4) {
            throw new IllegalStateException(
                    "Gateway Kafka required topics must be unique. eqpEvents="
                            + topicProperties.getEqpEvents()
                            + ", eqpCommands="
                            + topicProperties.getEqpCommands()
                            + ", uiEvents="
                            + topicProperties.getUiEvents()
                            + ", uiCommands="
                            + topicProperties.getUiCommands()
            );
        }
        return unique.stream().toList();
    }

    /**
     * tc.eqp.commands 파티션 수/소유 파티션 범위 불변식을 검증합니다.
     */
    private void verifyCommandTopicPartitionInvariant(final String topic, final int actualPartitionCount) {
        final int expectedPartitionCount = shardProperties.getCommandsPartitionCount();
        if (actualPartitionCount != expectedPartitionCount) {
            throw new IllegalStateException(
                    "Partition count mismatch for " + topic
                            + " (expected=" + expectedPartitionCount
                            + ", actual=" + actualPartitionCount + ")"
            );
        }

        for (Integer ownedPartition : shardProperties.getOwnedPartitions()) {
            if (ownedPartition == null || ownedPartition < 0 || ownedPartition >= actualPartitionCount) {
                throw new IllegalStateException(
                        "Owned partition out of range for " + topic + ": " + ownedPartition
                                + " (partitionCount=" + actualPartitionCount + ")"
                );
            }
        }
    }
}
