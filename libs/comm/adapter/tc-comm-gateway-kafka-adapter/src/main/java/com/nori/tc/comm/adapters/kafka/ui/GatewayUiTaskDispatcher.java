package com.nori.tc.comm.adapters.kafka.ui;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.gateway.config.GatewayUiTaskPolicyProperties;
import com.nori.tc.comm.gateway.metrics.GatewayDisposition;
import com.nori.tc.comm.gateway.metrics.GatewayDispositionMetrics;
import com.nori.tc.common.mailbox.MailboxScheduler;
import com.nori.tc.common.mailbox.MailboxTask;
import com.nori.tc.common.mailbox.execution.MailboxExecutionRuntime;
import com.nori.tc.common.task.execution.pipeline.runtime.KafkaTaskExecutionPipeline;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskDispatchReport;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskReplyStatus;
import com.nori.tc.messaging.kafka.starter.contract.KafkaMessageDispatcher;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

/**
 * Gateway UI Task 디스패처입니다.
 *
 * <p>핵심 목적은 poll 루프에서 무거운 처리 로직을 분리해,
 * Kafka 수신 스레드는 enqueue만 수행하고 실제 처리/응답은 mailbox 런타임에서 수행하도록
 * 실행 경계를 명확히 만드는 것입니다.</p>
 *
 * <p>처리 흐름:</p>
 * <p>1) subscriber -> {@link #dispatch(KafkaUiTaskMessage)} 호출</p>
 * <p>2) eqpId 라우팅 키 기준 mailbox enqueue</p>
 * <p>3) {@link MailboxExecutionRuntime}가 worker에서 {@link KafkaTaskExecutionPipeline} 실행</p>
 * <p>4) 결과 disposition 로그/메트릭 기록</p>
 */
@Component
public class GatewayUiTaskDispatcher implements KafkaMessageDispatcher<KafkaUiTaskMessage>, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskDispatcher.class);

    private static final String FLOW_UI_TASK = "UI_TASK";
    private static final int UNKNOWN_PARTITION = -1;
    private static final long UNKNOWN_OFFSET = -1L;
    private static final String UNKNOWN_TEXT = "N/A";

    private final KafkaTaskExecutionPipeline<KafkaUiTaskMessage> uiTaskPipeline;
    private final GatewayDispositionMetrics dispositionMetrics;
    private final GatewayKafkaTopicProperties topicProperties;
    private final GatewayUiTaskPolicyProperties policyProperties;

    private final MailboxScheduler<GatewayUiMailboxTask> mailboxScheduler;
    private final MailboxExecutionRuntime<GatewayUiMailboxTask> mailboxExecutionRuntime;

    private volatile boolean running;

    /**
     * UI Task 디스패처를 초기화합니다.
     *
     * @param uiTaskPipeline UI task 공통 처리 파이프라인
     * @param dispositionMetrics disposition 메트릭 수집기
     * @param topicProperties Kafka topic 프로퍼티
     * @param policyProperties UI task 정책 프로퍼티
     */
    public GatewayUiTaskDispatcher(
            final KafkaTaskExecutionPipeline<KafkaUiTaskMessage> uiTaskPipeline,
            final GatewayDispositionMetrics dispositionMetrics,
            final GatewayKafkaTopicProperties topicProperties,
            final GatewayUiTaskPolicyProperties policyProperties
    ) {
        this.uiTaskPipeline = Objects.requireNonNull(uiTaskPipeline, "uiTaskPipeline is null");
        this.dispositionMetrics = Objects.requireNonNull(dispositionMetrics, "dispositionMetrics is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.policyProperties = Objects.requireNonNull(policyProperties, "policyProperties is null");

        this.mailboxScheduler = new MailboxScheduler<>(policyProperties.getMailboxCapacity());

        final MailboxExecutionRuntime.Config runtimeConfig = MailboxExecutionRuntime.Config.async(
                policyProperties.getDispatcherThreads(),
                policyProperties.getWorkerThreads(),
                policyProperties.getRuntimeShutdownWaitMs(),
                "gateway-ui-mailbox-dispatcher-",
                "gateway-ui-mailbox-worker-"
        );

        this.mailboxExecutionRuntime = new MailboxExecutionRuntime<>(
                "gateway-ui-task-dispatch",
                mailboxScheduler,
                runtimeConfig,
                this::processMailboxTask,
                this::handleMailboxRejected,
                this::handleMailboxFailure,
                ex -> log.error("Gateway UI mailbox dispatcher loop failure.", ex)
        );
    }

    /**
     * UI task를 mailbox에 적재합니다.
     *
     * <p>이 메서드는 poll 경로에서 호출되므로, 실제 비즈니스 처리는 수행하지 않고
     * enqueue 성공/실패만 즉시 반환합니다.</p>
     *
     * @param message UI task 메시지
     */
    @Override
    public void dispatch(final KafkaUiTaskMessage message) {
        Objects.requireNonNull(message, "message is null");

        if (!isRunning()) {
            final String eqpId = extractEqpId(message);
            final String traceId = extractTraceId(message);
            recordDisposition(
                    GatewayDisposition.REJECTED,
                    "RUNTIME_NOT_RUNNING",
                    extractEventType(message),
                    eqpId,
                    traceId,
                    null,
                    null,
                    false
            );
            throw new IllegalStateException("Gateway UI mailbox runtime is not running");
        }

        final String eqpId = requireRoutingKey(message);
        final String traceId = extractTraceId(message);
        final String eventType = extractEventType(message);

        final GatewayUiMailboxTask task = new GatewayUiMailboxTask(
                message,
                eqpId,
                eventType,
                traceId,
                System.currentTimeMillis()
        );

        final boolean offered = mailboxScheduler.enqueue(task, task.enqueuedAtEpochMs());
        if (!offered) {
            recordDisposition(
                    GatewayDisposition.REJECTED,
                    "MAILBOX_OVERFLOW",
                    eventType,
                    eqpId,
                    traceId,
                    null,
                    null,
                    false
            );
            log.warn(
                    "Gateway UI mailbox enqueue rejected. topic={}, eqpId={}, traceId={}, eventType={}, mailboxCount={}, readyQueueSize={}",
                    topicProperties.getUiEvents(),
                    eqpId,
                    traceId,
                    eventType,
                    mailboxScheduler.mailboxCount(),
                    mailboxScheduler.readyQueueSize()
            );
            throw new IllegalStateException("Gateway UI mailbox overflow: eqpId=" + eqpId);
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "Gateway UI task enqueued. topic={}, eqpId={}, traceId={}, eventType={}, mailboxCount={}, readyQueueSize={}",
                    topicProperties.getUiEvents(),
                    eqpId,
                    traceId,
                    eventType,
                    mailboxScheduler.mailboxCount(),
                    mailboxScheduler.readyQueueSize()
            );
        }
    }

    /**
     * mailbox worker에서 실제 UI task 파이프라인을 실행합니다.
     *
     * @param task mailbox task
     * @throws Exception 파이프라인 처리 예외
     */
    private void processMailboxTask(final GatewayUiMailboxTask task) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug(
                    "Gateway UI mailbox task processing started. topic={}, eqpId={}, traceId={}, eventType={}, enqueuedAtEpochMs={}",
                    topicProperties.getUiEvents(),
                    task.routingKey(),
                    safeText(task.traceId()),
                    safeText(task.eventType()),
                    task.enqueuedAtEpochMs()
            );
        }

        final KafkaTaskDispatchReport report = uiTaskPipeline.dispatch(task.message());
        if (report.result().status() == KafkaTaskReplyStatus.FAIL) {
            recordDisposition(
                    GatewayDisposition.REJECTED,
                    "PIPELINE_FAIL",
                    task.eventType(),
                    task.routingKey(),
                    task.traceId(),
                    report.replyEventType(),
                    report.result().errorCode(),
                    report.duplicateSkipped()
            );
            return;
        }

        recordDisposition(
                GatewayDisposition.ACCEPTED,
                "PIPELINE_PASS",
                task.eventType(),
                task.routingKey(),
                task.traceId(),
                report.replyEventType(),
                report.result().errorCode(),
                report.duplicateSkipped()
        );
    }

    /**
     * worker 큐가 가득 차 task가 거절된 경우를 처리합니다.
     *
     * @param task 거절된 task
     * @param ex 거절 예외
     */
    private void handleMailboxRejected(final GatewayUiMailboxTask task, final RejectedExecutionException ex) {
        recordDisposition(
                GatewayDisposition.REJECTED,
                "WORKER_REJECTED",
                task.eventType(),
                task.routingKey(),
                task.traceId(),
                null,
                null,
                false
        );
        log.error(
                "Gateway UI mailbox worker rejected task. topic={}, eqpId={}, traceId={}, eventType={}, mailboxCount={}, readyQueueSize={}",
                topicProperties.getUiEvents(),
                task.routingKey(),
                safeText(task.traceId()),
                safeText(task.eventType()),
                mailboxScheduler.mailboxCount(),
                mailboxScheduler.readyQueueSize(),
                ex
        );
    }

    /**
     * mailbox worker 처리 중 예외가 발생한 경우를 처리합니다.
     *
     * @param task 실패 task
     * @param ex 처리 예외
     */
    private void handleMailboxFailure(final GatewayUiMailboxTask task, final Exception ex) {
        recordDisposition(
                GatewayDisposition.REJECTED,
                "PIPELINE_EXCEPTION",
                task.eventType(),
                task.routingKey(),
                task.traceId(),
                null,
                null,
                false
        );
        log.error(
                "Gateway UI mailbox task failed. topic={}, eqpId={}, traceId={}, eventType={}",
                topicProperties.getUiEvents(),
                task.routingKey(),
                safeText(task.traceId()),
                safeText(task.eventType()),
                ex
        );
    }

    /**
     * disposition 메트릭과 표준 로그를 기록합니다.
     *
     * @param disposition 처리 상태
     * @param reason 상태 사유
     * @param eventType 이벤트 타입
     * @param eqpId 장비 ID
     * @param traceId 추적 ID
     * @param replyEventType 응답 이벤트 타입
     * @param errorCode 오류 코드
     * @param duplicateSkipped 중복 스킵 여부
     */
    private void recordDisposition(
            final GatewayDisposition disposition,
            final String reason,
            final String eventType,
            final String eqpId,
            final String traceId,
            final String replyEventType,
            final String errorCode,
            final boolean duplicateSkipped
    ) {
        dispositionMetrics.increment(FLOW_UI_TASK, disposition);

        if (disposition == GatewayDisposition.ACCEPTED) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "GATEWAY_TASK_DISPOSITION. flow={}, disposition={}, reason={}, topic={}, partition={}, offset={}, eqpId={}, traceId={}, eventType={}, replyEventType={}, errorCode={}, duplicateSkipped={}",
                        FLOW_UI_TASK,
                        disposition,
                        reason,
                        topicProperties.getUiEvents(),
                        UNKNOWN_PARTITION,
                        UNKNOWN_OFFSET,
                        safeText(eqpId),
                        safeText(traceId),
                        safeText(eventType),
                        safeText(replyEventType),
                        safeText(errorCode),
                        duplicateSkipped
                );
            }
            return;
        }

        log.info(
                "GATEWAY_TASK_DISPOSITION. flow={}, disposition={}, reason={}, topic={}, partition={}, offset={}, eqpId={}, traceId={}, eventType={}, replyEventType={}, errorCode={}, duplicateSkipped={}",
                FLOW_UI_TASK,
                disposition,
                reason,
                topicProperties.getUiEvents(),
                UNKNOWN_PARTITION,
                UNKNOWN_OFFSET,
                safeText(eqpId),
                safeText(traceId),
                safeText(eventType),
                safeText(replyEventType),
                safeText(errorCode),
                duplicateSkipped
        );
    }

    /**
     * SmartLifecycle 시작: mailbox 런타임을 기동합니다.
     */
    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        mailboxExecutionRuntime.start();
        running = true;
        log.info(
                "Gateway UI mailbox runtime started. mailboxCapacity={}, dispatcherThreads={}, workerThreads={}, shutdownWaitMs={}",
                policyProperties.getMailboxCapacity(),
                policyProperties.getDispatcherThreads(),
                policyProperties.getWorkerThreads(),
                policyProperties.getRuntimeShutdownWaitMs()
        );
    }

    /**
     * SmartLifecycle 종료: mailbox 런타임을 중지합니다.
     */
    @Override
    public synchronized void stop() {
        running = false;
        mailboxExecutionRuntime.stop();
        log.info("Gateway UI mailbox runtime stopped.");
    }

    /**
     * isRunning 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    @Override
    public boolean isRunning() {
        return running && mailboxExecutionRuntime.isRunning();
    }

    /**
     * consumer보다 먼저 시작되도록 낮은 phase를 사용합니다.
     */
    @Override
    public int getPhase() {
        return -100;
    }

    /**
     * routing key(eqpId)를 강제 추출합니다.
     *
     * @param message UI task 메시지
     * @return eqpId
     */
    private static String requireRoutingKey(final KafkaUiTaskMessage message) {
        final String eqpId = extractEqpId(message);
        if (eqpId == null) {
            throw new IllegalArgumentException("UI task eqpId is required");
        }
        return eqpId;
    }

    /**
     * extractEqpId 기능을 수행합니다.
     *
     * @param message 입력 값
     * @return 처리 결과
     */

    private static String extractEqpId(final KafkaUiTaskMessage message) {
        if (message == null || message.data() == null) {
            return null;
        }
        return normalizeText(message.data().eqpId());
    }

    /**
     * extractTraceId 기능을 수행합니다.
     *
     * @param message 입력 값
     * @return 처리 결과
     */

    private static String extractTraceId(final KafkaUiTaskMessage message) {
        if (message == null || message.metadata() == null) {
            return null;
        }
        return normalizeText(message.metadata().traceId());
    }

    /**
     * extractEventType 기능을 수행합니다.
     *
     * @param message 입력 값
     * @return 처리 결과
     */

    private static String extractEventType(final KafkaUiTaskMessage message) {
        if (message == null || message.metadata() == null) {
            return null;
        }
        return normalizeText(message.metadata().eventType());
    }

    /**
     * safeText 기능을 수행합니다.
     *
     * @param value 입력 값
     * @return 처리 결과
     */

    private static String safeText(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN_TEXT;
        }
        return value.trim();
    }

    /**
     * normalizeText 기능을 수행합니다.
     *
     * @param value 입력 값
     * @return 처리 결과
     */

    private static String normalizeText(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }

    /**
     * Gateway UI task mailbox payload입니다.
     *
     * @param message 원본 UI task 메시지
     * @param routingKey mailbox 라우팅 키(eqpId)
     * @param eventType UI 이벤트 타입
     * @param traceId traceId
     * @param enqueuedAtEpochMs enqueue 시각(epoch ms)
     */
    private record GatewayUiMailboxTask(
            KafkaUiTaskMessage message,
            String routingKey,
            String eventType,
            String traceId,
            long enqueuedAtEpochMs
    ) implements MailboxTask {
    }
}
