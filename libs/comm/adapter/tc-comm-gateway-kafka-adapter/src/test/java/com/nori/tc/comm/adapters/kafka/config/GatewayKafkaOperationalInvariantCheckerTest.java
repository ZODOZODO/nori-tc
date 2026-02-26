package com.nori.tc.comm.adapters.kafka.config;

import com.nori.tc.comm.gateway.config.props.GatewayKafkaShardProperties;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartitionInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link GatewayKafkaOperationalInvariantChecker}의 U11 핵심 불변조건 로직(리플렉션 호출)을 검증하는 단위 테스트입니다.
 *
 * <p>테스트 범위:</p>
 * <p>- {@code verifyOwnedPartitionsInRange(...)} : ownedPartitions 범위 검증</p>
 * <p>- {@code verifyRoutingTopicPartitionConsistency(...)} : command/UI gateway 토픽 파티션 수 동일성 검증</p>
 *
 * <p>주의:</p>
 * <p>- {@link org.apache.kafka.clients.admin.AdminClient} 연동까지 포함한 {@code verify()} 전체 경로는 외부 의존성이 크므로,
 *   U16에서는 핵심 불변조건 메서드 자체를 리플렉션으로 호출해 회귀를 방지합니다.</p>
 */
class GatewayKafkaOperationalInvariantCheckerTest {

    /**
     * ownedPartitions가 실제 토픽 파티션 범위를 벗어나면 예외가 발생해야 합니다.
     */
    @Test
    @DisplayName("U11: ownedPartitions가 topic 파티션 범위를 벗어나면 기동 불변조건 검증이 실패한다")
    void shouldRejectOwnedPartitionOutOfRange() {
        final GatewayKafkaOperationalInvariantChecker checker = newChecker(List.of(0, 6));

        final RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> invokePrivate(
                        checker,
                        "verifyOwnedPartitionsInRange",
                        new Class<?>[]{String.class, int.class},
                        "tc.ui.events.gateway",
                        6
                )
        );

        assertInstanceOf(IllegalStateException.class, rootCause(thrown));
    }

    /**
     * command / UI gateway 토픽 파티션 수가 동일하면 불변조건 검증을 통과해야 합니다.
     */
    @Test
    @DisplayName("U11: command/UI gateway 토픽 파티션 수가 동일하면 라우팅 토픽 일관성 검증을 통과한다")
    void shouldPassWhenRoutingTopicsHaveSamePartitionCount() {
        final GatewayKafkaOperationalInvariantChecker checker = newChecker(List.of(0, 1));
        final Map<String, TopicDescription> descriptions = Map.of(
                "tc.eqp.commands", topicDescription("tc.eqp.commands", 6),
                "tc.ui.events.gateway", topicDescription("tc.ui.events.gateway", 6)
        );

        assertDoesNotThrow(() -> invokePrivate(
                checker,
                "verifyRoutingTopicPartitionConsistency",
                new Class<?>[]{Map.class, String.class, String.class},
                descriptions,
                "tc.eqp.commands",
                "tc.ui.events.gateway"
        ));
    }

    /**
     * command / UI gateway 토픽 파티션 수가 다르면 예외가 발생해야 합니다.
     */
    @Test
    @DisplayName("U11: command/UI gateway 토픽 파티션 수가 다르면 라우팅 토픽 일관성 검증이 실패한다")
    void shouldFailWhenRoutingTopicsHaveDifferentPartitionCount() {
        final GatewayKafkaOperationalInvariantChecker checker = newChecker(List.of(0, 1));
        final Map<String, TopicDescription> descriptions = Map.of(
                "tc.eqp.commands", topicDescription("tc.eqp.commands", 6),
                "tc.ui.events.gateway", topicDescription("tc.ui.events.gateway", 5)
        );

        final RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> invokePrivate(
                        checker,
                        "verifyRoutingTopicPartitionConsistency",
                        new Class<?>[]{Map.class, String.class, String.class},
                        descriptions,
                        "tc.eqp.commands",
                        "tc.ui.events.gateway"
                )
        );

        assertInstanceOf(IllegalStateException.class, rootCause(thrown));
    }

    /**
     * 테스트 대상 checker를 생성합니다.
     *
     * <p>U11 단위 테스트는 private 불변조건 메서드만 호출하므로,
     * 토픽/샤드 프로퍼티는 해당 메서드에 필요한 최소값만 세팅합니다.</p>
     *
     * @param ownedPartitions 테스트할 owned partition 목록
     * @return 테스트 대상 checker
     */
    private GatewayKafkaOperationalInvariantChecker newChecker(final List<Integer> ownedPartitions) {
        final GatewayKafkaClientProperties clientProperties = new GatewayKafkaClientProperties();

        final GatewayKafkaShardProperties shardProperties = new GatewayKafkaShardProperties();
        shardProperties.setCommandsPartitionCount(6);
        shardProperties.setOwnedPartitions(ownedPartitions);

        final GatewayKafkaTopicProperties topicProperties = new GatewayKafkaTopicProperties();
        topicProperties.setEqpCommands("tc.eqp.commands");
        topicProperties.setUiEvents("tc.ui.events.gateway");
        topicProperties.setEqpEvents("tc.eqp.events");
        topicProperties.setUiCommands("tc.ui.commands");
        topicProperties.setMesEvents("tc.mes.events");
        topicProperties.setMesCommands("tc.mes.commands");

        return new GatewayKafkaOperationalInvariantChecker(clientProperties, shardProperties, topicProperties);
    }

    /**
     * 테스트용 {@link TopicDescription}을 생성합니다.
     *
     * <p>Kafka AdminClient 응답 전체를 모사할 필요 없이, 파티션 수 검증에 필요한
     * 최소 정보(partitions 리스트 길이)만 구성합니다.</p>
     *
     * @param topicName 토픽명
     * @param partitionCount 파티션 수
     * @return TopicDescription
     */
    private TopicDescription topicDescription(final String topicName, final int partitionCount) {
        final Node leader = new Node(1, "localhost", 9092);
        final List<TopicPartitionInfo> partitions = java.util.stream.IntStream.range(0, partitionCount)
                .mapToObj(index -> new TopicPartitionInfo(index, leader, List.of(leader), List.of(leader)))
                .toList();
        return new TopicDescription(topicName, false, partitions);
    }

    /**
     * private 메서드를 리플렉션으로 호출합니다.
     *
     * <p>호출 대상 메서드에서 예외가 발생하면 {@link InvocationTargetException}의 cause를 그대로 재전달하여
     * 테스트가 실제 예외 타입을 검증할 수 있게 합니다.</p>
     *
     * @param target 호출 대상 객체
     * @param methodName 메서드명
     * @param parameterTypes 파라미터 타입 목록
     * @param args 호출 인자
     * @return 메서드 반환값 (void면 null)
     */
    private Object invokePrivate(
            final Object target,
            final String methodName,
            final Class<?>[] parameterTypes,
            final Object... args
    ) {
        try {
            final Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 테스트 검증 편의를 위해 루트 원인을 반환합니다.
     *
     * @param throwable 예외 객체
     * @return 루트 원인 (없으면 자기 자신)
     */
    private Throwable rootCause(final Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}

