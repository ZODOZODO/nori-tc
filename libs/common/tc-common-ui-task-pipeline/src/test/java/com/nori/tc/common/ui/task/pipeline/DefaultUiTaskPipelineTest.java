package com.nori.tc.common.ui.task.pipeline;

import com.nori.tc.common.kafka.processing.FixedRetryPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for {@link DefaultUiTaskPipeline}.
 */
class DefaultUiTaskPipelineTest {

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

        final DefaultUiTaskPipeline<TestRequest> pipeline = new DefaultUiTaskPipeline<>(
                new TestAccessor(),
                eventType -> Optional.of(new UiTaskProcessorSpec<>(
                        eventType,
                        "EQP_UPDATE_REP",
                        req -> {
                            processCount.incrementAndGet();
                            return UiTaskResult.pass();
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

        final UiTaskDispatchReport report = pipeline.dispatch(request);

        Assertions.assertTrue(report.duplicateSkipped());
        Assertions.assertEquals(UiTaskReplyStatus.PASS, report.result().status());
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

        final DefaultUiTaskPipeline<TestRequest> pipeline = new DefaultUiTaskPipeline<>(
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

        final UiTaskDispatchReport report = pipeline.dispatch(new TestRequest("EQP_CREATE", "TRACE-2", "EQP-2"));

        Assertions.assertFalse(report.duplicateSkipped());
        Assertions.assertEquals(UiTaskReplyStatus.FAIL, report.result().status());
        Assertions.assertEquals(UiTaskPipelineErrorCode.HANDLER_NOT_FOUND, report.result().errorCode());
        Assertions.assertEquals(1, dlqReporter.records.size());
        Assertions.assertEquals(UiTaskPipelineStage.ROUTING, dlqReporter.records.get(0).stage);
    }

    /**
     * Verifies processor retry succeeds before retry policy exhausts.
     */
    @Test
    void shouldRetryProcessorAndEventuallyPass() {
        final AtomicInteger processCount = new AtomicInteger(0);

        final UiTaskProcessorRegistry<TestRequest> registry = eventType -> Optional.of(
                new UiTaskProcessorSpec<>(
                        eventType,
                        eventType + "_REP",
                        request -> {
                            int count = processCount.incrementAndGet();
                            if (count == 1) {
                                throw new IllegalStateException("first failure");
                            }
                            return UiTaskResult.pass();
                        }
                )
        );

        final DefaultUiTaskPipeline<TestRequest> pipeline = new DefaultUiTaskPipeline<>(
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

        final UiTaskDispatchReport report = pipeline.dispatch(new TestRequest("EQP_END", "TRACE-3", "EQP-3"));

        Assertions.assertEquals(UiTaskReplyStatus.PASS, report.result().status());
        Assertions.assertEquals(2, processCount.get());
    }

    /**
     * Verifies reply publish failure after retries throws UiTaskReplyPublishException.
     */
    @Test
    void shouldThrowWhenReplyPublishRetryExhausted() {
        final DefaultUiTaskPipeline<TestRequest> pipeline = new DefaultUiTaskPipeline<>(
                new TestAccessor(),
                eventType -> Optional.of(new UiTaskProcessorSpec<>(eventType, eventType + "_REP", request -> UiTaskResult.pass())),
                new CapturingReplyPublisher(10),
                new CapturingDlqReporter(),
                new InMemoryDedupStore(),
                new FixedRetryPolicy(1, 0L),
                new FixedRetryPolicy(2, 0L),
                60_000L,
                System::currentTimeMillis
        );

        Assertions.assertThrows(UiTaskReplyPublishException.class, () ->
                pipeline.dispatch(new TestRequest("EQP_START", "TRACE-4", "EQP-4"))
        );
    }

    private record TestRequest(String eventType, String traceId, String eqpId) {
    }

    private static final class TestAccessor implements UiTaskMessageAccessor<TestRequest> {

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

    private static final class InMemoryDedupStore implements UiTaskDeduplicationStore {

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

    private static final class CapturingReplyPublisher implements UiTaskReplyPublisher<TestRequest> {

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
                final UiTaskResult result
        ) throws Exception {
            int current = publishAttempt.incrementAndGet();
            if (current <= failTimes) {
                throw new Exception("reply publish failed");
            }
            published.add(new PublishedReply(request, replyEventType, result));
        }
    }

    private static final class CapturingDlqReporter implements UiTaskDlqReporter<TestRequest> {

        private final List<DlqRecord> records = new ArrayList<>();

        @Override
        public void report(
                final TestRequest request,
                final UiTaskPipelineStage stage,
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
            UiTaskResult result
    ) {
    }

    private record DlqRecord(
            UiTaskPipelineStage stage,
            String reasonCode,
            String reasonMessage,
            String replyEventType
    ) {
    }
}

