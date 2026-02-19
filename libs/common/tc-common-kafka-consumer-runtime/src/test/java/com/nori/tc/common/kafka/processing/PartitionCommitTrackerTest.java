package com.nori.tc.common.kafka.processing;

import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

/**
 * Unit tests for {@link PartitionCommitTracker}.
 */
class PartitionCommitTrackerTest {

    /**
     * Verifies out-of-order completion can advance commit only after gap is closed.
     */
    @Test
    void shouldAdvanceCommitOffsetOnlyWhenCompletionBecomesContiguous() {
        final PartitionCommitTracker tracker = new PartitionCommitTracker(
                new TopicPartition("tc.eqp.events", 0),
                100L
        );

        tracker.recordCompletedOffset(101L);
        OptionalLong firstPoll = tracker.pollCommittableOffset();
        Assertions.assertTrue(firstPoll.isEmpty());

        tracker.recordCompletedOffset(100L);
        OptionalLong secondPoll = tracker.pollCommittableOffset();
        Assertions.assertTrue(secondPoll.isPresent());
        Assertions.assertEquals(102L, secondPoll.getAsLong());
    }

    /**
     * Verifies duplicate/old offsets do not produce additional commit plan.
     */
    @Test
    void shouldIgnoreOldOffsetsAfterCommitAdvanced() {
        final PartitionCommitTracker tracker = new PartitionCommitTracker(
                new TopicPartition("tc.mes.events", 1),
                10L
        );

        tracker.recordCompletedOffset(10L);
        OptionalLong firstCommit = tracker.pollCommittableOffset();
        Assertions.assertTrue(firstCommit.isPresent());
        Assertions.assertEquals(11L, firstCommit.getAsLong());

        tracker.recordCompletedOffset(10L);
        OptionalLong secondCommit = tracker.pollCommittableOffset();
        Assertions.assertTrue(secondCommit.isEmpty());
    }
}

