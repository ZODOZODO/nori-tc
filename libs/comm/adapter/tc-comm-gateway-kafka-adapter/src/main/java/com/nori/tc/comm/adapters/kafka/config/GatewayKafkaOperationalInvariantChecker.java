package com.nori.tc.comm.adapters.kafka.config;

import com.nori.tc.comm.gateway.config.props.GatewayKafkaShardProperties;
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
 * Gateway Kafka 운영 불변조건(startup invariant) 검증기입니다.
 *
 * <p>애플리케이션 시작 직후 아래 조건을 검증합니다.
 * 1) 필수 topic이 모두 존재하는지
 * 2) topic 파티션 수가 1 이상인지
 * 3) Gateway 고정 라우팅 대상 토픽({@code tc.eqp.commands}, {@code tc.ui.events.gateway})의 파티션 정책이 일관적인지
 * 4) {@code tc.eqp.commands} 파티션 수가 shard 설정과 일치하는지
 * 5) ownedPartitions가 실제 라우팅 대상 토픽 파티션 범위를 벗어나지 않는지</p>
 */
@Component
public class GatewayKafkaOperationalInvariantChecker {

    private static final Logger log = LoggerFactory.getLogger(GatewayKafkaOperationalInvariantChecker.class);

    private final GatewayKafkaClientProperties kafkaClientProperties;
    private final GatewayKafkaShardProperties shardProperties;
    private final GatewayKafkaTopicProperties topicProperties;

    /**
     * 불변조건 검증에 필요한 의존성을 초기화합니다.
     *
     * @param kafkaClientProperties Kafka admin 설정
     * @param shardProperties shard/partition 정책
     * @param topicProperties topic 매핑 설정
     */
    public GatewayKafkaOperationalInvariantChecker(
            final GatewayKafkaClientProperties kafkaClientProperties,
            final GatewayKafkaShardProperties shardProperties,
            final GatewayKafkaTopicProperties topicProperties
    ) {
        this.kafkaClientProperties = Objects.requireNonNull(kafkaClientProperties, "kafkaClientProperties is null");
        this.shardProperties = Objects.requireNonNull(shardProperties, "shardProperties is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
    }

    /**
     * 애플리케이션 시작 후 Kafka 토픽/파티션 불변조건을 검증합니다.
     *
     * <p>검증 실패 시 즉시 예외를 발생시켜 잘못된 설정으로 구동되는 것을 차단합니다.</p>
     */
    @PostConstruct
    public void verify() {
        final List<String> requiredTopics = buildRequiredTopics();
        final Map<String, Object> adminProps = kafkaClientProperties.buildAdminProperties();
        final String commandTopic = topicProperties.getEqpCommands();
        final String uiGatewayTopic = topicProperties.getUiEvents();

        log.info("Gateway Kafka 기동 불변조건 검증 시작. requiredTopics={}, commandTopic={}, uiGatewayTopic={}, expectedCommandPartitions={}, ownedPartitions={}",
                requiredTopics,
                commandTopic,
                uiGatewayTopic,
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

                if (log.isDebugEnabled()) {
                    log.debug("Kafka topic metadata 확인 완료. topic={}, partitionCount={}", topic, partitionCount);
                }

                if (topic.equals(commandTopic)) {
                    verifyCommandTopicPartitionInvariant(topic, partitionCount);
                }
                if (topic.equals(uiGatewayTopic)) {
                    verifyUiGatewayTopicPartitionInvariant(topic, partitionCount);
                }
            }

            verifyRoutingTopicPartitionConsistency(topicDescriptions, commandTopic, uiGatewayTopic);

            log.info("Gateway Kafka 기동 불변조건 검증 완료. topics={}, commandTopic={}, commandPartitions={}, uiGatewayTopic={}, uiGatewayPartitions={}",
                    requiredTopics,
                    commandTopic,
                    topicDescriptions.get(commandTopic).partitions().size(),
                    uiGatewayTopic,
                    topicDescriptions.get(uiGatewayTopic).partitions().size());
            if (log.isDebugEnabled()) {
                log.debug("Kafka topic descriptions={}", topicDescriptions);
            }
        } catch (Exception ex) {
            log.error("Gateway Kafka 기동 불변조건 검증 실패. requiredTopics={}", requiredTopics, ex);
            throw new IllegalStateException("Failed to verify Kafka startup invariants", ex);
        }
    }

    /**
     * Gateway 필수 topic 목록을 중복 없이 구성합니다.
     *
     * @return 필수 topic 목록
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
     * command topic 파티션 관련 불변조건을 검증합니다.
     *
     * @param topic command topic
     * @param actualPartitionCount 실제 파티션 수
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

        verifyOwnedPartitionsInRange(topic, actualPartitionCount);

        if (log.isDebugEnabled()) {
            log.debug("Command topic 파티션 불변조건 검증 완료. topic={}, partitionCount={}, expectedPartitionCount={}, ownedPartitions={}",
                    topic,
                    actualPartitionCount,
                    expectedPartitionCount,
                    shardProperties.getOwnedPartitions());
        }
    }

    /**
     * UI Gateway 토픽 파티션 관련 불변조건을 검증합니다.
     *
     * <p>U11부터 Gateway UI 이벤트 토픽도 고정 partition(assign) 방식으로 소비하므로,
     * {@code ownedPartitions}가 실제 UI Gateway 토픽 파티션 범위를 벗어나지 않는지 기동 시 검증합니다.</p>
     *
     * @param topic UI Gateway topic
     * @param actualPartitionCount 실제 파티션 수
     */
    private void verifyUiGatewayTopicPartitionInvariant(final String topic, final int actualPartitionCount) {
        verifyOwnedPartitionsInRange(topic, actualPartitionCount);

        if (log.isDebugEnabled()) {
            log.debug("UI Gateway topic 파티션 불변조건 검증 완료. topic={}, partitionCount={}, ownedPartitions={}",
                    topic,
                    actualPartitionCount,
                    shardProperties.getOwnedPartitions());
        }
    }

    /**
     * 고정 라우팅 대상 두 토픽(command / UI gateway)의 파티션 수 일치 여부를 검증합니다.
     *
     * <p>U11부터 두 토픽 모두 동일한 {@code ownedPartitions} 집합을 기준으로 ASSIGN 소비하므로,
     * 파티션 수가 다르면 동일 partition 정책을 적용할 수 없습니다.</p>
     *
     * @param topicDescriptions Kafka AdminClient 조회 결과
     * @param commandTopic command topic 이름
     * @param uiGatewayTopic UI gateway topic 이름
     */
    private void verifyRoutingTopicPartitionConsistency(
            final Map<String, TopicDescription> topicDescriptions,
            final String commandTopic,
            final String uiGatewayTopic
    ) {
        final TopicDescription commandDescription = topicDescriptions.get(commandTopic);
        final TopicDescription uiGatewayDescription = topicDescriptions.get(uiGatewayTopic);
        if (commandDescription == null || uiGatewayDescription == null) {
            throw new IllegalStateException("Routing topic descriptions must exist for invariant verification");
        }

        final int commandPartitionCount = commandDescription.partitions().size();
        final int uiGatewayPartitionCount = uiGatewayDescription.partitions().size();
        if (commandPartitionCount != uiGatewayPartitionCount) {
            throw new IllegalStateException(
                    "Routing topic partition count mismatch. commandTopic=" + commandTopic
                            + " (partitions=" + commandPartitionCount + ")"
                            + ", uiGatewayTopic=" + uiGatewayTopic
                            + " (partitions=" + uiGatewayPartitionCount + ")"
            );
        }

        log.info("고정 라우팅 대상 토픽 파티션 수 일치 검증 완료. commandTopic={}, uiGatewayTopic={}, partitionCount={}",
                commandTopic,
                uiGatewayTopic,
                commandPartitionCount);
    }

    /**
     * 현재 Gateway 인스턴스의 {@code ownedPartitions}가 실제 topic 파티션 범위 내에 있는지 검증합니다.
     *
     * <p>검증 실패 시 잘못된 ASSIGN 설정으로 인해 consumer 시작 자체가 실패하거나,
     * 일부 파티션만 소비되는 운영 오류가 발생할 수 있으므로 즉시 기동을 차단합니다.</p>
     *
     * @param topic 검증 대상 topic 이름
     * @param actualPartitionCount 실제 topic 파티션 수
     */
    private void verifyOwnedPartitionsInRange(final String topic, final int actualPartitionCount) {
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
