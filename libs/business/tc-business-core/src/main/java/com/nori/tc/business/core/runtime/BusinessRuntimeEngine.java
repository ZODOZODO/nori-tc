package com.nori.tc.business.core.runtime;

import com.nori.tc.business.core.config.BusinessCoreRuntimeProperties;
import com.nori.tc.business.core.dlq.BusinessDlqPublisherPort;
import com.nori.tc.business.core.logging.BusinessLogContext;
import com.nori.tc.business.core.logging.BusinessObservationLogger;
import com.nori.tc.business.core.modelcache.BusinessModelRuntimeProvider;
import com.nori.tc.business.domain.dlq.BusinessDlqMessage;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.runtime.BusinessMessageType;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.core.ui.BusinessUiTaskExecutor;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionExecutionException;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionExecutor;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterEvaluationException;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowMatchResult;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowMatcher;
import com.nori.tc.common.consumer.runtime.AckEvent;
import com.nori.tc.common.consumer.runtime.AckQueue;
import com.nori.tc.common.consumer.runtime.AckStatus;
import com.nori.tc.common.consumer.runtime.ConsumerPartition;
import com.nori.tc.common.consumer.runtime.PartitionCommitCoordinator;
import com.nori.tc.common.consumer.runtime.FixedRetryPolicy;
import com.nori.tc.common.mailbox.MailboxScheduler;
import com.nori.tc.common.mailbox.execution.MailboxExecutionRuntime;
import com.nori.tc.common.task.execution.policy.dlq.TaskDlqRecordFactory;
import com.nori.tc.common.task.execution.policy.runtime.TaskHandlingPolicyEvaluator;
import com.nori.tc.common.task.execution.policy.types.DlqRecord;
import com.nori.tc.common.task.execution.policy.types.TaskFailureCategory;
import com.nori.tc.common.task.execution.policy.types.TaskFailureContext;
import com.nori.tc.common.task.execution.policy.types.TaskHandlingAction;
import com.nori.tc.common.task.execution.policy.types.TaskHandlingDecision;
import com.nori.tc.common.task.execution.policy.timeout.TaskTimeoutExceededException;
import com.nori.tc.common.task.execution.policy.timeout.TimeoutBoundRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BusinessRuntimeEngine 클래스입니다.
 *
 * <p>비즈니스 메시지 처리의 공통 계약과 실행 경계를 정의하고,
 * 호출 계층에서 일관된 방식으로 사용할 수 있도록 설계했습니다.</p>
 */
@Component
public class BusinessRuntimeEngine implements SmartLifecycle, BusinessTaskIngressPort {

    private static final Logger log = LoggerFactory.getLogger(BusinessRuntimeEngine.class);
    /**
     * 트레이스 ID를 얻을 수 없을 때 사용하는 기본 값입니다.
     */

    private final BusinessCoreRuntimeProperties properties;
    private final BusinessModelRuntimeProvider modelRuntimeProvider;
    private final BusinessUiTaskExecutor uiTaskExecutor;
    private final BusinessWorkflowMatcher workflowMatcher;
    private final BusinessWorkflowActionExecutor workflowActionExecutor;
    private final BusinessDlqPublisherPort dlqPublisherPort;
    private final MailboxScheduler<BusinessMailboxTask> mailboxScheduler;
    private final TaskHandlingPolicyEvaluator taskHandlingPolicy;
    private final BusinessRuntimeDispositionMetrics dispositionMetrics;
    private final MailboxExecutionRuntime<BusinessMailboxTask> mailboxExecutionRuntime;

    private final Map<String, TopicRuntime> topicRuntimes = new java.util.concurrent.ConcurrentHashMap<>();

    private ScheduledExecutorService timeoutScheduler;

    private volatile boolean running = false;

    /**
     * 스프링 빈 주입 지점입니다.
     * 외부에서 제공되는 메트릭 구현체가 없으면 기본 구현을 사용합니다.
     */
    @Autowired
    public BusinessRuntimeEngine(
            final BusinessCoreRuntimeProperties properties,
            final BusinessModelRuntimeProvider modelRuntimeProvider,
            final BusinessUiTaskExecutor uiTaskExecutor,
            final BusinessWorkflowMatcher workflowMatcher,
            final BusinessWorkflowActionExecutor workflowActionExecutor,
            final BusinessDlqPublisherPort dlqPublisherPort,
            final ObjectProvider<BusinessRuntimeDispositionMetrics> dispositionMetricsProvider
    ) {
        this(
                properties,
                modelRuntimeProvider,
                uiTaskExecutor,
                workflowMatcher,
                workflowActionExecutor,
                dlqPublisherPort,
                dispositionMetricsProvider.getIfAvailable(BusinessRuntimeDispositionMetrics::new)
        );
    }

    /**
     * 테스트/내부 구성에서 직접 사용하는 기본 생성자입니다.
     * 필수 의존성을 검증하고 큐/정책/실행 런타임을 구성합니다.
     */
    BusinessRuntimeEngine(
            final BusinessCoreRuntimeProperties properties,
            final BusinessModelRuntimeProvider modelRuntimeProvider,
            final BusinessUiTaskExecutor uiTaskExecutor,
            final BusinessWorkflowMatcher workflowMatcher,
            final BusinessWorkflowActionExecutor workflowActionExecutor,
            final BusinessDlqPublisherPort dlqPublisherPort,
            final BusinessRuntimeDispositionMetrics dispositionMetrics
    ) {
        this.properties = Objects.requireNonNull(properties, "properties is null");
        this.modelRuntimeProvider = Objects.requireNonNull(modelRuntimeProvider, "modelRuntimeProvider is null");
        this.uiTaskExecutor = Objects.requireNonNull(uiTaskExecutor, "uiTaskExecutor is null");
        this.workflowMatcher = Objects.requireNonNull(workflowMatcher, "workflowMatcher is null");
        this.workflowActionExecutor = Objects.requireNonNull(workflowActionExecutor, "workflowActionExecutor is null");
        this.dlqPublisherPort = Objects.requireNonNull(dlqPublisherPort, "dlqPublisherPort is null");
        this.dispositionMetrics = Objects.requireNonNull(dispositionMetrics, "dispositionMetrics is null");
        this.mailboxScheduler = new MailboxScheduler<>(properties.getRuntime().getMailboxCapacity());
        this.taskHandlingPolicy = new TaskHandlingPolicyEvaluator(
                new FixedRetryPolicy(
                        properties.getRuntime().getRetryMaxAttempts(),
                        properties.getRuntime().getRetryBackoffMs()
                ),
                new TaskDlqRecordFactory(300),
                true
        );
        this.mailboxExecutionRuntime = createMailboxExecutionRuntime();
    }

    /**
     * DLQ/메트릭 기본값을 사용하는 간소화 생성자입니다.
     */
    BusinessRuntimeEngine(
            final BusinessCoreRuntimeProperties properties,
            final BusinessModelRuntimeProvider modelRuntimeProvider,
            final BusinessUiTaskExecutor uiTaskExecutor,
            final BusinessWorkflowMatcher workflowMatcher,
            final BusinessWorkflowActionExecutor workflowActionExecutor
    ) {
        this(
                properties,
                modelRuntimeProvider,
                uiTaskExecutor,
                workflowMatcher,
                workflowActionExecutor,
                BusinessDlqPublisherPort.noop(),
                new BusinessRuntimeDispositionMetrics()
        );
    }

    /**
     * 최소 의존성으로 엔진을 생성할 때 사용하는 생성자입니다.
     * 주로 단위 테스트 또는 샘플 실행에 사용됩니다.
     */
    BusinessRuntimeEngine(final BusinessCoreRuntimeProperties properties) {
        this(
                properties,
                BusinessModelRuntimeProvider.noop(),
                BusinessUiTaskExecutor.noop(),
                BusinessWorkflowMatcher.noop(),
                BusinessWorkflowActionExecutor.noop(),
                BusinessDlqPublisherPort.noop(),
                new BusinessRuntimeDispositionMetrics()
        );
    }

    /**
     * 런타임 엔진을 시작합니다.
     *
     * <p>토픽별 런타임을 등록하고 소비 스레드를 기동한 뒤,
     * mailbox 실행 런타임을 시작하여 실제 작업 처리를 활성화합니다.</p>
     */
    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;

        final BusinessCoreRuntimeProperties.Runtime runtime = properties.getRuntime();
        this.timeoutScheduler = Executors.newScheduledThreadPool(
                runtime.getTimeoutSchedulerThreads(),
                namedThreadFactory("biz-timeout-")
        );

        registerTopicRuntimes();
        startTopicConsumers();
        mailboxExecutionRuntime.start();

        log.info("Business runtime engine started. topics={}, dispatcherThreads={}, workerThreads={}, timeoutThreads={}",
                topicRuntimes.keySet(),
                runtime.getDispatcherThreads(),
                runtime.getWorkerThreads(),
                runtime.getTimeoutSchedulerThreads());
    }

    /**
     * 런타임 엔진을 안전하게 중지합니다.
     *
     * <p>토픽 소비 스레드, mailbox 런타임, timeout 스케줄러를 순서대로 종하여
     * 잔여 작업이 불필요하게 누적되지 않도록 정리합니다.</p>
     */
    @Override
    public synchronized void stop() {
        running = false;

        for (TopicRuntime topicRuntime : topicRuntimes.values()) {
            shutdownExecutor(topicRuntime.consumerPool(), "topic-consumer:" + topicRuntime.topicName());
        }
        topicRuntimes.clear();

        mailboxExecutionRuntime.stop();
        shutdownExecutor(timeoutScheduler, "timeout-scheduler");

        timeoutScheduler = null;

        log.info("Business runtime engine stopped.");
    }

    /**
     * 현재 런타임 엔진이 실행 중인지 반환합니다.
     *
     * @return 실행 중이면 {@code true}, 아니면 {@code false}
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 스프링 라이프사이클 phase 값을 반환합니다.
     *
     * @return 현재 구현의 고정 phase 값(0)
     */
    @Override
    public int getPhase() {
        return 0;
    }

    /**
     * 외부에서 전달된 인바운드 레코드를 토픽 큐에 적재합니다.
     *
     * @param record 수신한 비즈니스 인바운드 레코드
     * @return 큐 적재에 성공하면 {@code true}, 실패하면 {@code false}
     */
    @Override
    public boolean submit(final BusinessInboundRecord record) {
        Objects.requireNonNull(record, "record is null");
        if (!running) {
            recordDisposition(record, BusinessRuntimeDisposition.REJECTED, "RUNTIME_NOT_RUNNING");
            log.warn("Business runtime is not running. topic={}, eqpId={}", record.topic(), record.eqpId());
            return false;
        }

        final TopicRuntime topicRuntime = topicRuntimes.get(record.topic());
        if (topicRuntime == null) {
            recordDisposition(record, BusinessRuntimeDisposition.REJECTED, "UNKNOWN_TOPIC");
            throw new IllegalArgumentException("Unknown topic: " + record.topic());
        }

        final boolean offered = topicRuntime.inboundQueue().offer(record);
        if (!offered) {
            recordDisposition(record, BusinessRuntimeDisposition.REJECTED, "TOPIC_QUEUE_OVERFLOW");
            log.error("Topic inbound queue overflow. topic={}, eqpId={}, partition={}, offset={}",
                    record.topic(), record.eqpId(), record.partition(), record.offset());
        }
        return offered;
    }

    /**
     * 설정값을 기준으로 토픽별 런타임 구성을 생성/등록합니다.
     */
    private void registerTopicRuntimes() {
        final BusinessCoreRuntimeProperties.Kafka kafka = properties.getKafka();
        final int queueCapacity = properties.getRuntime().getTopicQueueCapacity();
        final int ackDrainMaxBatch = properties.getRuntime().getAckDrainMaxBatch();

        topicRuntimes.put(
                kafka.getEqpEventsTopic(),
                new TopicRuntime(
                        kafka.getEqpEventsTopic(),
                        BusinessMessageType.EQP,
                        queueCapacity,
                        ackDrainMaxBatch,
                        kafka.getEqpEventsConsumerThreads(),
                        "biz-consumer-eqp-"
                )
        );
        topicRuntimes.put(
                kafka.getMesEventsTopic(),
                new TopicRuntime(
                        kafka.getMesEventsTopic(),
                        BusinessMessageType.MES,
                        queueCapacity,
                        ackDrainMaxBatch,
                        kafka.getMesEventsConsumerThreads(),
                        "biz-consumer-mes-"
                )
        );
        topicRuntimes.put(
                kafka.getUiEventsTopic(),
                new TopicRuntime(
                        kafka.getUiEventsTopic(),
                        BusinessMessageType.UI,
                        queueCapacity,
                        ackDrainMaxBatch,
                        kafka.getUiEventsConsumerThreads(),
                        "biz-consumer-ui-"
                )
        );
    }

    /**
     * 등록된 토픽 런타임마다 소비 루프를 비동기로 시작합니다.
     */
    private void startTopicConsumers() {
        for (TopicRuntime topicRuntime : topicRuntimes.values()) {
            topicRuntime.consumerPool().execute(() -> runTopicConsumerLoop(topicRuntime));
        }
    }

    /**
     * mailbox 실행 런타임을 생성합니다.
     *
     * <p>dispatcher/worker 스레드 수, 처리 함수, 예외 콜백을 한 곳에서 구성하여
     * 런타임 시작/중지 시 일관된 실행 정책을 적용합니다.</p>
     *
     * @return 구성된 mailbox 실행 런타임
     */
    private MailboxExecutionRuntime<BusinessMailboxTask> createMailboxExecutionRuntime() {
        final BusinessCoreRuntimeProperties.Runtime runtime = properties.getRuntime();
        final MailboxExecutionRuntime.Config runtimeConfig = MailboxExecutionRuntime.Config.async(
                runtime.getDispatcherThreads(),
                runtime.getWorkerThreads(),
                3_000L,
                "biz-dispatcher-",
                "biz-worker-"
        );

        return new MailboxExecutionRuntime<>(
                "business-runtime-engine",
                mailboxScheduler,
                runtimeConfig,
                this::processTask,
                this::handleWorkerRejectedTask,
                (task, ex) -> log.error("Business task 처리 중 예외가 발생했습니다. topic={}, eqpId={}, partition={}, offset={}",
                        task.record().topic(),
                        task.record().eqpId(),
                        task.record().partition(),
                        task.record().offset(),
                        ex),
                ex -> log.error("Business mailbox dispatcher 실행 중 예외가 발생했습니다.", ex)
        );
    }

    /**
     * 단일 토픽 런타임에 대한 소비 루프를 실행합니다.
     *
     * @param topicRuntime 처리 대상 토픽 런타임
     */
    private void runTopicConsumerLoop(final TopicRuntime topicRuntime) {
        while (running) {
            try {
                drainAckAndPrepareCommit(topicRuntime);

                final BusinessInboundRecord record = topicRuntime.inboundQueue().poll(200, TimeUnit.MILLISECONDS);
                if (record == null) {
                    continue;
                }

                /*
                 * 코어 계층은 Kafka SDK 타입을 직접 사용하지 않습니다.
                 * 토픽/파티션 식별은 중립 값 객체(ConsumerPartition)로 관리하고,
                 * 실제 Kafka 커밋 타입 변환은 Kafka adapter/runtime 계층에서 수행합니다.
                 */
                final ConsumerPartition topicPartition = new ConsumerPartition(record.topic(), record.partition());
                topicRuntime.commitCoordinator().registerPartitionIfAbsent(topicPartition, record.offset());

                final BusinessMailboxTask task = new BusinessMailboxTask(record, 1);
                final boolean offered = mailboxScheduler.enqueue(task, now());
                if (!offered) {
                    log.error("Mailbox enqueue overflow. topic={}, eqpId={}, partition={}, offset={}",
                            record.topic(), record.eqpId(), record.partition(), record.offset());
                    recordDisposition(record, BusinessRuntimeDisposition.DLQ, "MAILBOX_ENQUEUE_OVERFLOW");
                    emitAck(record, AckStatus.DLQ);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.error("Topic consumer loop failed. topic={}", topicRuntime.topicName(), ex);
            }
        }
    }

    /**
     * ACK 큐를 배치로 비우고 커밋 가능한 오프셋을 계산합니다.
     *
     * @param topicRuntime 처리 대상 토픽 런타임
     */
    private void drainAckAndPrepareCommit(final TopicRuntime topicRuntime) {
        final List<AckEvent> drained = new ArrayList<>();
        final int drainedCount = topicRuntime.ackQueue().drainTo(drained, topicRuntime.ackDrainMaxBatch());
        if (drainedCount == 0) {
            return;
        }

        for (AckEvent ackEvent : drained) {
            topicRuntime.commitCoordinator().applyAck(ackEvent);
        }

        /*
         * 커밋 준비 결과는 Kafka 타입이 아닌 중립 커밋 계획(Map<ConsumerPartition, Long>)으로 유지합니다.
         * 코어는 "어떤 파티션을 어디까지 전진 가능한지"만 판단하고, 브로커 전용 타입 변환 책임은 가지지 않습니다.
         */
        final Map<ConsumerPartition, Long> commitOffsets = topicRuntime.commitCoordinator().collectCommitOffsets();
        if (!commitOffsets.isEmpty() && log.isDebugEnabled()) {
            log.debug("Prepared commit offsets. topic={}, offsets={}", topicRuntime.topicName(), commitOffsets);
        }
    }

    /**
     * 워커 스레드 풀이 포화되어 작업이 거부되었을 때 호출됩니다.
     *
     * <p>거부된 작업은 즉시 DLQ 처리 대상으로 분류하고 ACK를 발행하여
     * 커밋 파이프라인이 해당 레코드를 정리할 수 있도록 합니다.</p>
     */
    private void handleWorkerRejectedTask(
            final BusinessMailboxTask task,
            final RejectedExecutionException rejected
    ) {
        /*
         * worker reject 콜백은 비동기 경계에서 실행되므로 기존 MDC 컨텍스트가 유지되지 않을 수 있습니다.
         * 따라서 task에 포함된 eqpId를 기준으로 MDC를 다시 주입해 로그 상관관계를 보장합니다.
         */
        try (BusinessLogContext ignored = BusinessLogContext.withEqpAndTraceId(
                task.record().eqpId(),
                task.record().traceId()
        )) {
            log.error("Worker pool rejected task. eqpId={}, topic={}, partition={}, offset={}",
                    task.record().eqpId(),
                    task.record().topic(),
                    task.record().partition(),
                    task.record().offset(),
                    rejected);
            recordDisposition(task.record(), BusinessRuntimeDisposition.DLQ, "WORKER_POOL_REJECTED");
            emitAck(task.record(), AckStatus.DLQ);
        }
    }

    /**
     * mailbox worker가 실제 비즈니스 작업을 처리하는 진입점입니다.
     *
     * @param task 처리할 mailbox 작업
     */
    private void processTask(final BusinessMailboxTask task) {
        /*
         * worker 진입 시점에 eqpId 기반 MDC를 설정합니다.
         * 성공/실패/재시도 분기 로그를 동일한 상관키로 추적하기 위함입니다.
         */
        try (BusinessLogContext ignored = BusinessLogContext.withEqpAndTraceId(
                task.record().eqpId(),
                task.record().traceId()
        )) {
            BusinessObservationLogger.logTaskStarted(
                    task.record().topic(),
                    task.record().eqpId(),
                    task.record().partition(),
                    task.record().offset(),
                    String.valueOf(task.record().messageType()),
                    task.record().messageName()
            );

            try {
                final TaskExecutionOutcome outcome = executeWithTimeout(task);
                if (outcome == TaskExecutionOutcome.WORKFLOW_NOT_FOUND) {
                    log.info("BIZ_TASK_NO_WORKFLOW_MATCHED. topic={}, eqpId={}, messageName={}, partition={}, offset={}",
                            task.record().topic(),
                            task.record().eqpId(),
                            task.record().messageName(),
                            task.record().partition(),
                            task.record().offset());
                    recordDisposition(task.record(), BusinessRuntimeDisposition.ACCEPTED, "WORKFLOW_NOT_FOUND");
                } else {
                    recordDisposition(task.record(), BusinessRuntimeDisposition.ACCEPTED, "PROCESSED");
                }
                emitAck(task.record(), AckStatus.SUCCESS);
            } catch (TaskTimeoutExceededException timeout) {
                handleFailure(task, TaskFailureCategory.TIMEOUT, timeout, true);
            } catch (BusinessWorkflowFilterEvaluationException filterEx) {
                handleFailure(task, TaskFailureCategory.FILTER_EVAL, filterEx, false);
            } catch (BusinessWorkflowActionExecutionException actionEx) {
                handleFailure(task, TaskFailureCategory.ACTION_EXEC, actionEx, false);
            } catch (Exception ex) {
                handleFailure(task, TaskFailureCategory.UNKNOWN, ex, false);
            }
        }
    }

    /**
     * 단일 작업을 timeout 경계 안에서 실행합니다.
     *
     * @param task 실행할 mailbox 작업
     * @return 작업 실행 결과
     * @throws Exception 작업 실행 중 발생한 예외
     */
    private TaskExecutionOutcome executeWithTimeout(final BusinessMailboxTask task) throws Exception {
        final TimeoutBoundRunner timeoutBoundRunner = new TimeoutBoundRunner(
                timeoutScheduler,
                properties.getRuntime().getTaskTimeoutMs()
        );
        return timeoutBoundRunner.run(() -> executeTask(task));
    }

    /**
     * 메시지 타입(UI/비UI)에 따라 실제 실행 경로를 분기합니다.
     *
     * @param task 실행할 mailbox 작업
     * @return 작업 실행 결과
     */
    private TaskExecutionOutcome executeTask(final BusinessMailboxTask task) {
        if (task.record().messageType() == BusinessMessageType.UI) {
            executeUiTask(task);
            return TaskExecutionOutcome.SUCCESS;
        }

        return executeNonUiTask(task);
    }

    /**
     * UI 메시지를 처리하는 전용 실행 경로입니다.
     *
     * @param task 실행할 mailbox 작업
     */

    private void executeUiTask(final BusinessMailboxTask task) {
        try {
            final var report = uiTaskExecutor.execute(task.record());
            if (log.isDebugEnabled()) {
                log.debug("UI task executed. eqpId={}, messageName={}, replyEventType={}, status={}, duplicateSkipped={}",
                        task.record().eqpId(),
                        task.record().messageName(),
                        report.replyEventType(),
                        report.result().status(),
                        report.duplicateSkipped());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("UI task execution failed", ex);
        }
    }

    /**
     * 비UI 메시지(EQP/MES)를 모델 런타임 + 워크플로우 기반으로 처리합니다.
     *
     * @param task 실행할 mailbox 작업
     * @return 작업 실행 결과
     */

    private TaskExecutionOutcome executeNonUiTask(final BusinessMailboxTask task) {
        final BusinessInboundRecord record = task.record();

        final TcModelRuntime modelRuntime = modelRuntimeProvider.findRuntimeByEqpId(record.eqpId())
                .orElse(null);
        if (modelRuntime == null) {
            if (log.isDebugEnabled()) {
                log.debug("Model runtime not found for eqpId. eqpId={}, messageName={}",
                        record.eqpId(),
                        record.messageName());
            }
            return TaskExecutionOutcome.WORKFLOW_NOT_FOUND;
        }

        final BusinessWorkflowMatchResult matchResult = workflowMatcher.match(record, modelRuntime);
        if (!matchResult.hasMatchedWorkflow()) {
            return TaskExecutionOutcome.WORKFLOW_NOT_FOUND;
        }

        workflowActionExecutor.execute(record, modelRuntime, matchResult);

        if (log.isDebugEnabled()) {
            log.debug("Non-UI task executed. eqpId={}, messageName={}, matchedWorkflowCount={}",
                    record.eqpId(),
                    record.messageName(),
                    matchResult.matchedWorkflows().size());
        }

        return TaskExecutionOutcome.SUCCESS;
    }

    /**
     * 작업 실패 시 재시도/즉시 DLQ/드롭 정책을 적용합니다.
     */
    private void handleFailure(
            final BusinessMailboxTask task,
            final TaskFailureCategory failureCategory,
            final Exception ex,
            final boolean timeoutTriggered
    ) {
        final BusinessInboundRecord record = task.record();
        final TaskFailureContext context = new TaskFailureContext(
                record.topic(),
                record.partition(),
                record.offset(),
                record.eqpId(),
                record.messageType().name(),
                record.messageName(),
                task.attempt(),
                record.payloadRef(),
                failureCategory,
                ex,
                timeoutTriggered,
                now()
        );

        final TaskHandlingDecision decision = taskHandlingPolicy.decide(context);
        final TaskHandlingAction action = decision.action();

        if (action == TaskHandlingAction.RETRY) {
            recordDisposition(record, BusinessRuntimeDisposition.RETRY, "RETRY_SCHEDULED_" + decision.finalCategory());
            emitAck(record, AckStatus.RETRY_SCHEDULED);
            scheduleRetry(task, decision.retryBackoffMs());
            return;
        }

        if (action == TaskHandlingAction.DLQ) {
            recordDisposition(record, BusinessRuntimeDisposition.DLQ, "DLQ_" + decision.finalCategory());
            log.warn("Task moved to DLQ. topic={}, eqpId={}, partition={}, offset={}, category={}, payloadRef={}, reason={}",
                    record.topic(),
                    record.eqpId(),
                    record.partition(),
                    record.offset(),
                    decision.finalCategory(),
                    record.payloadRef(),
                    decision.dlqRecord() == null ? "n/a" : decision.dlqRecord().exceptionMessage());
            publishRuntimeDlq(task, decision);
            emitAck(record, AckStatus.DLQ);
            return;
        }

        if (action == TaskHandlingAction.CONTINUE) {
            recordDisposition(record, BusinessRuntimeDisposition.ACCEPTED, "POLICY_CONTINUE_" + decision.finalCategory());
            emitAck(record, AckStatus.SUCCESS);
            return;
        }

        recordDisposition(record, BusinessRuntimeDisposition.REJECTED, "FAILED_" + decision.finalCategory());
        log.error("Task failed without retry/DLQ. topic={}, eqpId={}, partition={}, offset={}, category={}",
                record.topic(),
                record.eqpId(),
                record.partition(),
                record.offset(),
                decision.finalCategory(),
                ex);
        emitAck(record, AckStatus.FAILED);
    }

    /**
     * 지정된 backoff 이후 동일 작업을 재시도하도록 예약합니다.
     *
     * @param task 재시도할 작업
     * @param backoffMs 재시도 지연 시간(ms)
     */
    private void scheduleRetry(final BusinessMailboxTask task, final long backoffMs) {
        try {
            timeoutScheduler.schedule(() -> {
                if (!running) {
                    return;
                }
                final BusinessMailboxTask retryTask = task.nextAttempt();
                final boolean offered = mailboxScheduler.enqueue(retryTask, now());
                if (!offered) {
                    log.error("Retry enqueue overflow. topic={}, eqpId={}, partition={}, offset={}, nextAttempt={}",
                            retryTask.record().topic(),
                            retryTask.record().eqpId(),
                            retryTask.record().partition(),
                            retryTask.record().offset(),
                            retryTask.attempt());
                    recordDisposition(retryTask.record(), BusinessRuntimeDisposition.DLQ, "RETRY_ENQUEUE_OVERFLOW");
                    emitAck(retryTask.record(), AckStatus.DLQ);
                }
            }, backoffMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException rejected) {
            log.error("Retry schedule rejected. topic={}, eqpId={}, partition={}, offset={}, attempt={}",
                    task.record().topic(),
                    task.record().eqpId(),
                    task.record().partition(),
                    task.record().offset(),
                    task.attempt(),
                    rejected);
            recordDisposition(task.record(), BusinessRuntimeDisposition.REJECTED, "RETRY_SCHEDULE_REJECTED");
            emitAck(task.record(), AckStatus.FAILED);
        }
    }

    /**
     * 정책 결정 결과를 기반으로 DLQ 메시지를 발행합니다.
     *
     * @param task 실패한 원본 작업
     * @param decision 실패 처리 정책 결정 결과
     */
    private void publishRuntimeDlq(final BusinessMailboxTask task, final TaskHandlingDecision decision) {
        final BusinessInboundRecord record = task.record();
        final DlqRecord dlqRecord = decision.dlqRecord();
        final String reasonMessage = resolveReasonMessage(
                dlqRecord == null ? null : dlqRecord.exceptionMessage(),
                "Runtime task moved to DLQ"
        );

        final Map<String, String> tags = new HashMap<>();
        tags.put("failureCategory", decision.finalCategory().name());
        tags.put("attempt", String.valueOf(task.attempt()));
        if (dlqRecord != null) {
            tags.put("sourceTopic", dlqRecord.sourceTopic());
            tags.put("sourcePartition", String.valueOf(dlqRecord.sourcePartition()));
            tags.put("sourceOffset", String.valueOf(dlqRecord.sourceOffset()));
            tags.put("exceptionClass", dlqRecord.exceptionClass());
            tags.put("attempts", String.valueOf(dlqRecord.attempts()));
            tags.put("occurredAtEpochMs", String.valueOf(dlqRecord.occurredAtEpochMs()));
        }

        final BusinessDlqMessage dlqMessage = new BusinessDlqMessage(
                UUID.randomUUID().toString(),
                "BUSINESS_RUNTIME",
                "PROCESS",
                decision.finalCategory().name(),
                reasonMessage,
                now(),
                record.topic(),
                record.partition(),
                record.offset(),
                record.eqpId(),
                record.messageType().name(),
                record.messageName(),
                record.traceId(),
                record.payloadRef(),
                tags
        );

        try {
            dlqPublisherPort.publish(dlqMessage);
            if (log.isDebugEnabled()) {
                log.debug("Runtime DLQ published. source={}, stage={}, reasonCode={}, topic={}, eqpId={}, partition={}, offset={}",
                        dlqMessage.source(),
                        dlqMessage.stage(),
                        dlqMessage.reasonCode(),
                        dlqMessage.topic(),
                        dlqMessage.eqpId(),
                        dlqMessage.partition(),
                        dlqMessage.offset());
            }
        } catch (Exception publishFailure) {
            log.error("Runtime DLQ publish failed. source={}, stage={}, reasonCode={}, topic={}, eqpId={}, partition={}, offset={}",
                    dlqMessage.source(),
                    dlqMessage.stage(),
                    dlqMessage.reasonCode(),
                    dlqMessage.topic(),
                    dlqMessage.eqpId(),
                    dlqMessage.partition(),
                    dlqMessage.offset(),
                    publishFailure);
        }
    }

    /**
     * 실패 사유 메시지를 정규화합니다.
     *
     * @param message 원본 메시지
     * @param fallback 원본이 비어 있을 때 사용할 대체 메시지
     * @return trim이 적용된 최종 메시지
     */
    private static String resolveReasonMessage(final String message, final String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message.trim();
    }

    /**
     * 처리 결과 ACK 이벤트를 토픽 ACK 큐에 적재합니다.
     *
     * @param record ACK 대상 레코드
     * @param status ACK 상태
     */
    private void emitAck(final BusinessInboundRecord record, final AckStatus status) {
        final TopicRuntime topicRuntime = topicRuntimes.get(record.topic());
        if (topicRuntime == null) {
            log.error("Ack target topic runtime not found. topic={}, eqpId={}, partition={}, offset={}, status={}",
                    record.topic(), record.eqpId(), record.partition(), record.offset(), status);
            return;
        }

        topicRuntime.ackQueue().offer(new AckEvent(
                record.topic(),
                record.partition(),
                record.offset(),
                status,
                now()
        ));
    }

    /**
     * 처리 결과(disposition) 메트릭/로그를 기록합니다.
     */
    private void recordDisposition(
            final BusinessInboundRecord record,
            final BusinessRuntimeDisposition disposition,
            final String reason
    ) {
        final String flow = resolveDispositionFlow(record);
        dispositionMetrics.increment(flow, disposition);
        if (disposition == BusinessRuntimeDisposition.ACCEPTED) {
            if (log.isDebugEnabled()) {
                log.debug("BUSINESS_TASK_DISPOSITION. flow={}, disposition={}, reason={}, topic={}, partition={}, offset={}, eqpId={}, traceId={}, messageName={}",
                        flow,
                        disposition,
                        reason,
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.eqpId(),
                        record.traceId(),
                        record.messageName());
            }
            return;
        }

        log.info("BUSINESS_TASK_DISPOSITION. flow={}, disposition={}, reason={}, topic={}, partition={}, offset={}, eqpId={}, traceId={}, messageName={}",
                flow,
                disposition,
                reason,
                record.topic(),
                record.partition(),
                record.offset(),
                record.eqpId(),
                record.traceId(),
                record.messageName());
    }

    /**
     * 메시지 타입에 대응되는 disposition flow 이름을 반환합니다.
     *
     * @param record 분류 대상 레코드
     * @return flow 이름(EQP_EVENT/MES_EVENT/UI_EVENT/UNKNOWN_EVENT)
     */
    private String resolveDispositionFlow(final BusinessInboundRecord record) {
        if (record == null || record.messageType() == null) {
            return "UNKNOWN_EVENT";
        }
        return switch (record.messageType()) {
            case EQP -> "EQP_EVENT";
            case MES -> "MES_EVENT";
            case UI -> "UI_EVENT";
        };
    }

    /**
     * 현재 시스템 시간을 epoch milliseconds로 반환합니다.
     *
     * @return 현재 시각(ms)
     */

    private static long now() {
        return System.currentTimeMillis();
    }

    /**
     * 지정된 접두어를 갖는 daemon 스레드 팩토리를 생성합니다.
     *
     * @param prefix 생성할 스레드 이름 접두어
     * @return 순번이 부여된 daemon 스레드 팩토리
     */
    private static ThreadFactory namedThreadFactory(final String prefix) {
        final AtomicInteger sequence = new AtomicInteger(0);
        return runnable -> {
            final Thread thread = new Thread(runnable);
            thread.setName(prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * 실행기를 중단하고 제한 시간 내 종료를 기다립니다.
     *
     * @param executor 종료할 실행기
     * @param name 로그 식별용 실행기 이름
     */
    private static void shutdownExecutor(final ExecutorService executor, final String name) {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                if (log.isDebugEnabled()) {
                    log.debug("Executor did not terminate in time. name={}", name);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 토픽 단위 실행 리소스(인바운드 큐, ACK 큐, 커밋 코디네이터, 소비 스레드 풀) 묶음입니다.
     */
    private record TopicRuntime(
            String topicName,
            BusinessMessageType messageType,
            BlockingQueue<BusinessInboundRecord> inboundQueue,
            AckQueue ackQueue,
            PartitionCommitCoordinator commitCoordinator,
            int ackDrainMaxBatch,
            ExecutorService consumerPool
    ) {
        private TopicRuntime(
                final String topicName,
                final BusinessMessageType messageType,
                final int queueCapacity,
                final int ackDrainMaxBatch,
                final int consumerThreads,
                final String threadNamePrefix
        ) {
            this(
                    topicName,
                    messageType,
                    new LinkedBlockingQueue<>(queueCapacity),
                    new AckQueue(),
                    new PartitionCommitCoordinator(),
                    ackDrainMaxBatch,
                    Executors.newFixedThreadPool(consumerThreads, namedThreadFactory(threadNamePrefix))
            );
        }
    }

    /**
     * 단일 작업 실행 결과를 표현합니다.
     */
    private enum TaskExecutionOutcome {
        SUCCESS,
        WORKFLOW_NOT_FOUND
    }
}
