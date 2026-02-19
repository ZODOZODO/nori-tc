package com.nori.tc.common.kafka.processing;

import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Unit tests for {@link PartitionCommitCoordinator}.
 */
class PartitionCommitCoordinatorTest {

    /**
     * Verifies non-commit-eligible ack does not advance offset and commit-eligible
     * out-of-order acks are handled correctly.
     */
    @Test
    void shouldBuildCommitPlanFromEligibleAcksOnly() {
        final PartitionCommitCoordinator coordinator = new PartitionCommitCoordinator();
        final TopicPartition partition = new TopicPartition("tc.ui.events", 2);

        coordinator.registerPartition(partition, 200L);

        coordinator.applyAck(new AckEvent(
                "tc.ui.events",
                2,
                201L,
                AckStatus.SUCCESS,
                System.currentTimeMillis()
        ));

        Map<TopicPartition, OffsetAndMetadata> firstPlan = coordinator.collectCommitOffsets();
        Assertions.assertTrue(firstPlan.isEmpty());

        coordinator.applyAck(new AckEvent(
                "tc.ui.events",
                2,
                200L,
                AckStatus.RETRY_SCHEDULED,
                System.currentTimeMillis()
        ));

        Map<TopicPartition, OffsetAndMetadata> secondPlan = coordinator.collectCommitOffsets();
        Assertions.assertTrue(secondPlan.isEmpty());

        coordinator.applyAck(new AckEvent(
                "tc.ui.events",
                2,
                200L,
                AckStatus.DLQ,
                System.currentTimeMillis()
        ));

        Map<TopicPartition, OffsetAndMetadata> thirdPlan = coordinator.collectCommitOffsets();
        Assertions.assertEquals(1, thirdPlan.size());
        Assertions.assertEquals(202L, thirdPlan.get(partition).offset());
    }
}

