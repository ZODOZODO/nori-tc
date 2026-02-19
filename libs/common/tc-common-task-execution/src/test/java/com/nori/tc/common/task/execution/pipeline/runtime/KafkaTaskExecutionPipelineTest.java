package com.nori.tc.common.task.execution.pipeline.runtime;

import com.nori.tc.common.kafka.processing.FixedRetryPolicy;
import com.nori.tc.common.task.execution.pipeline.constants.KafkaTaskPipelineErrorKeys;
import com.nori.tc.common.task.execution.pipeline.constants.KafkaTaskPipelineStage;
import com.nori.tc.common.task.execution.pipeline.exception.KafkaTaskReplyPublishException;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskDeduplicationStore;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskDlqReporter;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskMessageAccessor;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskProcessorRegistry;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskReplyPublisher;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskDispatchReport;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskProcessorSpec;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskReplyStatus;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link KafkaTaskExecutionPipeline} 동작 검증 테스트입니다.
 */
class KafkaTaskExecutionPipelineTest {

    /**
     * traceId 중복이면 처리기를 건너뛰고 PASS가 반환되는지 검증합니다.
     */
    @Test
    void shouldSkipProcessorWhenTraceIdAlreadyProcessed() {
        final AtomicInteger processCount = new AtomicInteger(0);
        final CapturingReplyPublisher replyPublisher = new CapturingReplyPublisher(0);
        final InMemoryDedupStore dedupStore = new InMemoryDedupStore();

        final TestRequest request = new TestRequest("EQP_UPDATE", "TRACE-1", "EQP-1");
        dedupStore.markProcessed("TRACE-1", 10_000L, 1_000L);

        final KafkaTaskExecutionPipeline<TestRequest> pipeline = new KafkaTaskExecutionPipeline<>(
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
     * 처리기 미등록 이벤트는 FAIL + DLQ 분기로 처리되는지 검증합니다.
     */
    @Test
    void shouldFailWhenHandlerNotFound() {
        final CapturingReplyPublisher replyPublisher = new CapturingReplyPublisher(0);
        final CapturingDlqReporter dlqReporter = new CapturingDlqReporter();

        final KafkaTaskExecutionPipeline<TestRequest> pipeline = new KafkaTaskExecutionPipeline<>(
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
        Assertions.assertEquals(KafkaTaskPipelineErrorKeys.HANDLER_NOT_FOUND, report.result().errorCode());
        Assertions.assertEquals(1, dlqReporter.records.size());
        Assertions.assertEquals(KafkaTaskPipelineStage.ROUTING, dlqReporter.records.get(0).stage);
    }

    /**
     * 처리기 실패가 재시도 후 복구되어 PASS가 반환되는지 검증합니다.
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

        final KafkaTaskExecutionPipeline<TestRequest> pipeline = new KafkaTaskExecutionPipeline<>(
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
     * 응답 발행 재시도 소진 시 예외를 던지는지 검증합니다.
     */
    @Test
    void shouldThrowWhenReplyPublishRetryExhausted() {
        final KafkaTaskExecutionPipeline<TestRequest> pipeline = new KafkaTaskExecutionPipeline<>(
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

    /**
     * 테스트용 요청 모델입니다.
     */
    private record TestRequest(String eventType, String traceId, String eqpId) {
    }

    /**
     * 테스트용 메시지 접근자입니다.
     */
    private static final class TestAccessor implements KafkaTaskMessageAccessor<TestRequest> {

        /**
         * 테스트 요청의 eventType을 반환합니다.
         */
        @Override
        public String eventType(final TestRequest request) {
            return request.eventType();
        }

        /**
         * 테스트 요청의 traceId를 반환합니다.
         */
        @Override
        public String traceId(final TestRequest request) {
            return request.traceId();
        }

        /**
         * 테스트 요청의 eqpId를 반환합니다.
         */
        @Override
        public String eqpId(final TestRequest request) {
            return request.eqpId();
        }
    }

    /**
     * 테스트용 인메모리 중복 저장소입니다.
     */
    private static final class InMemoryDedupStore implements KafkaTaskDeduplicationStore {

        private final Map<String, Long> expiresAtByTrace = new java.util.concurrent.ConcurrentHashMap<>();

        /**
         * traceId가 TTL 범위 내에 등록되어 있는지 검사합니다.
         */
        @Override
        public boolean isProcessed(final String traceId, final long nowEpochMs) {
            final Long expiresAt = expiresAtByTrace.get(traceId);
            return expiresAt != null && expiresAt >= nowEpochMs;
        }

        /**
         * traceId 만료 시각을 저장합니다.
         */
        @Override
        public void markProcessed(final String traceId, final long ttlMs, final long nowEpochMs) {
            expiresAtByTrace.put(traceId, nowEpochMs + ttlMs);
        }
    }

    /**
     * 테스트용 응답 발행 캡처 구현입니다.
     */
    private static final class CapturingReplyPublisher implements KafkaTaskReplyPublisher<TestRequest> {

        private final int failTimes;
        private final AtomicInteger publishAttempt = new AtomicInteger(0);
        private final List<PublishedReply> published = new ArrayList<>();

        /**
         * 지정 횟수만큼 발행 실패를 강제하는 테스트용 발행기를 생성합니다.
         */
        private CapturingReplyPublisher(final int failTimes) {
            this.failTimes = failTimes;
        }

        /**
         * 지정 횟수 내에는 예외를 발생시키고, 이후에는 발행 이력을 기록합니다.
         */
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

    /**
     * 테스트용 DLQ 캡처 구현입니다.
     */
    private static final class CapturingDlqReporter implements KafkaTaskDlqReporter<TestRequest> {

        private final List<DlqRecord> records = new ArrayList<>();

        /**
         * DLQ 보고 이력을 메모리에 축적합니다.
         */
        @Override
        public void report(
                final TestRequest request,
                final KafkaTaskPipelineStage stage,
                final String reasonKey,
                final String reasonMessage,
                final String replyEventType
        ) {
            records.add(new DlqRecord(stage, reasonKey, reasonMessage, replyEventType));
        }
    }

    /**
     * 테스트용 응답 발행 이력 모델입니다.
     */
    private record PublishedReply(
            TestRequest request,
            String replyEventType,
            KafkaTaskResult result
    ) {
    }

    /**
     * 테스트용 DLQ 보고 이력 모델입니다.
     */
    private record DlqRecord(
            KafkaTaskPipelineStage stage,
            String reasonKey,
            String reasonMessage,
            String replyEventType
    ) {
    }
}
