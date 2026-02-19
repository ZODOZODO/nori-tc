package com.nori.tc.common.kafka.processing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FixedRetryPolicy}.
 */
class FixedRetryPolicyTest {

    /**
     * Verifies retry decision sequence with fixed max attempts.
     */
    @Test
    void shouldStopRetryWhenAttemptReachesMaxAttempts() {
        final FixedRetryPolicy policy = new FixedRetryPolicy(3, 250L);
        final RuntimeException failure = new RuntimeException("test");

        RetryDecision first = policy.evaluate(1, failure);
        RetryDecision second = policy.evaluate(2, failure);
        RetryDecision third = policy.evaluate(3, failure);

        Assertions.assertTrue(first.shouldRetry());
        Assertions.assertEquals(250L, first.backoffMs());

        Assertions.assertTrue(second.shouldRetry());
        Assertions.assertEquals(250L, second.backoffMs());

        Assertions.assertFalse(third.shouldRetry());
        Assertions.assertEquals(0L, third.backoffMs());
    }

    /**
     * Verifies constructor validation for invalid arguments.
     */
    @Test
    void shouldRejectInvalidConstructorArguments() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new FixedRetryPolicy(0, 0L));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new FixedRetryPolicy(1, -1L));
    }
}

