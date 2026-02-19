package com.nori.tc.common.kafka.task.pipeline;

import com.nori.tc.common.kafka.processing.FixedRetryPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for {@link DefaultKafkaTaskPipeline}.
 */
class DefaultKafkaTaskPipelineTest {

    /**
     * Verifies duplicate traceId path returns PASS and skips processor execution.
     */
    @Test
    void shouldSkipProcessorWhenTraceIdAlreadyProcessed() {
        final AtomicInteger processCount = new AtomicInteger(0);
        final CapturingReplyPublisher replyPublisher = new CapturingReplyPublisher(0);
        final InMemoryDedupStore dedupStore = new InMemoryDedupStore();

        final TestRequest request = new TestRequest("EQP_UPDATE", "TRACE-1", "EQP-1");
        dedupStore.markProcessed("TRACE-1", 10_000L, 1_000L);

        final DefaultKafkaTaskPipeline<TestRequest> pipeline = new DefaultKafkaTaskPipeline<>(
                new TestAccessor(),
                eventType -> Optional.of(new KafkaTaskProcessorSpec<>(
                        eventType,
                        "EQP_UPDATE_REP",
                        req -> {
                            processCount.incrementAndGet();
                            return KafkaTaskResult.pass();
                        }
                )),
                replyPublisher,
                new CapturingDlqReporter(),
                dedupStore,
                new FixedRetryPolicy(2, 0L),
                new FixedRetryPolicy(2, 0L),
                60_000L,
                () -> 2_000L
        );

        final KafkaTaskDispatchReport report = pipeline.dispatch(request);

        Assertions.assertTrue(report.duplicateSkipped());
        Assertions.assertEquals(KafkaTaskReplyStatus.PASS, report.result().status());
        Assertions.assertEquals(0, processCount.get());
        Assertions.assertEquals(1, replyPublisher.published.size());
        Assertions.assertEquals("EQP_UPDATE_REP", replyPublisher.published.get(0).replyEventType);
    }

    /**
     * Verifies missing handler path reports DLQ and returns FAIL result.
     */
    @Test
    void shouldFailWhenHandlerNotFound() {
        final CapturingReplyPublisher replyPublisher = new CapturingReplyPublisher(0);
        final CapturingDlqReporter dlqReporter = new CapturingDlqReporter();

        final DefaultKafkaTaskPipeline<TestRequest> pipeline = new DefaultKafkaTaskPipeline<>(
                new TestAccessor(),
                eventType -> Optional.empty(),
                replyPublisher,
                dlqReporter,
                new InMemoryDedupStore(),
                new FixedRetryPolicy(1, 0L),
                new FixedRetryPolicy(1, 0L),
                60_000L,
                () -> 1_000L
        );

        final KafkaTaskDispatchReport report = pipeline.dispatch(new TestRequest("EQP_CREATE", "TRACE-2", "EQP-2"));

        Assertions.assertFalse(report.duplicateSkipped());
        Assertions.assertEquals(KafkaTaskReplyStatus.FAIL, report.result().status());
        Assertions.assertEquals(KafkaTaskPipelineErrorCode.HANDLER_NOT_FOUND, report.result().errorCode());
        Assertions.assertEquals(1, dlqReporter.records.size());
        Assertions.assertEquals(KafkaTaskPipelineStage.ROUTING, dlqReporter.records.get(0).stage);
    }

    /**
     * Verifies processor retry succeeds before retry policy exhausts.
     */
    @Test
    void shouldRetryProcessorAndEventuallyPass() {
        final AtomicInteger processCount = new AtomicInteger(0);

        final KafkaTaskProcessorRegistry<TestRequest> registry = eventType -> Optional.of(
                new KafkaTaskProcessorSpec<>(
                        eventType,
                        eventType + "_REP",
                        request -> {
                            int count = processCount.incrementAndGet();
                            if (count == 1) {
                                throw new IllegalStateException("first failure");
                            }
                            return KafkaTaskResult.pass();
                        }
                )
        );

        final DefaultKafkaTaskPipeline<TestRequest> pipeline = new DefaultKafkaTaskPipeline<>(
                new TestAccessor(),
                registry,
                new CapturingReplyPublisher(0),
                new CapturingDlqReporter(),
                new InMemoryDedupStore(),
                new FixedRetryPolicy(2, 0L),
                new FixedRetryPolicy(1, 0L),
                60_000L,
                System::currentTimeMillis
        );

        final KafkaTaskDispatchReport report = pipeline.dispatch(new TestRequest("EQP_END", "TRACE-3", "EQP-3"));

        Assertions.assertEquals(KafkaTaskReplyStatus.PASS, report.result().status());
        Assertions.assertEquals(2, processCount.get());
    }

    /**
     * Verifies reply publish failure after retries throws KafkaTaskReplyPublishException.
     */
    @Test
    void shouldThrowWhenReplyPublishRetryExhausted() {
        final DefaultKafkaTaskPipeline<TestRequest> pipeline = new DefaultKafkaTaskPipeline<>(
                new TestAccessor(),
                eventType -> Optional.of(new KafkaTaskProcessorSpec<>(eventType, eventType + "_REP", request -> KafkaTaskResult.pass())),
                new CapturingReplyPublisher(10),
                new CapturingDlqReporter(),
                new InMemoryDedupStore(),
                new FixedRetryPolicy(1, 0L),
                new FixedRetryPolicy(2, 0L),
                60_000L,
                System::currentTimeMillis
        );

        Assertions.assertThrows(KafkaTaskReplyPublishException.class, () ->
                pipeline.dispatch(new TestRequest("EQP_START", "TRACE-4", "EQP-4"))
        );
    }

    private record TestRequest(String eventType, String traceId, String eqpId) {
    }

    private static final class TestAccessor implements KafkaTaskMessageAccessor<TestRequest> {

        @Override
        public String eventType(final TestRequest request) {
            return request.eventType();
        }

        @Override
        public String traceId(final TestRequest request) {
            return request.traceId();
        }

        @Override
        public String eqpId(final TestRequest request) {
            return request.eqpId();
        }
    }

    private static final class InMemoryDedupStore implements KafkaTaskDeduplicationStore {

        private final Map<String, Long> expiresAtByTrace = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public boolean isProcessed(final String traceId, final long nowEpochMs) {
            final Long expiresAt = expiresAtByTrace.get(traceId);
            return expiresAt != null && expiresAt >= nowEpochMs;
        }

        @Override
        public void markProcessed(final String traceId, final long ttlMs, final long nowEpochMs) {
            expiresAtByTrace.put(traceId, nowEpochMs + ttlMs);
        }
    }

    private static final class CapturingReplyPublisher implements KafkaTaskReplyPublisher<TestRequest> {

        private final int failTimes;
        private final AtomicInteger publishAttempt = new AtomicInteger(0);
        private final List<PublishedReply> published = new ArrayList<>();

        private CapturingReplyPublisher(final int failTimes) {
            this.failTimes = failTimes;
        }

        @Override
        public void publishResult(
                final TestRequest request,
                final String replyEventType,
                final KafkaTaskResult result
        ) throws Exception {
            int current = publishAttempt.incrementAndGet();
            if (current <= failTimes) {
                throw new Exception("reply publish failed");
            }
            published.add(new PublishedReply(request, replyEventType, result));
        }
    }

    private static final class CapturingDlqReporter implements KafkaTaskDlqReporter<TestRequest> {

        private final List<DlqRecord> records = new ArrayList<>();

        @Override
        public void report(
                final TestRequest request,
                final KafkaTaskPipelineStage stage,
                final String reasonCode,
                final String reasonMessage,
                final String replyEventType
        ) {
            records.add(new DlqRecord(stage, reasonCode, reasonMessage, replyEventType));
        }
    }

    private record PublishedReply(
            TestRequest request,
            String replyEventType,
            KafkaTaskResult result
    ) {
    }

    private record DlqRecord(
            KafkaTaskPipelineStage stage,
            String reasonCode,
            String reasonMessage,
            String replyEventType
    ) {
    }
}



