package com.nori.tc.business.core.runtime;

import com.nori.tc.business.core.config.BusinessCoreRuntimeProperties;
import com.nori.tc.business.core.dlq.BusinessDlqPublisherPort;
import com.nori.tc.business.core.modelcache.BusinessModelRuntimeProvider;
import com.nori.tc.business.domain.dlq.BusinessDlqMessage;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.runtime.BusinessMessageType;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.core.ui.BusinessUiTaskExecutor;
import com.nori.tc.business.core.workflow.BusinessWorkflowActionExecutionException;
import com.nori.tc.business.core.workflow.BusinessWorkflowActionExecutor;
import com.nori.tc.business.core.workflow.BusinessWorkflowFilterEvaluationException;
import com.nori.tc.business.core.workflow.BusinessWorkflowMatchResult;
import com.nori.tc.business.core.workflow.BusinessWorkflowMatcher;
import com.nori.tc.common.kafka.processing.AckEvent;
import com.nori.tc.common.kafka.processing.AckQueue;
import com.nori.tc.common.kafka.processing.AckStatus;
import com.nori.tc.common.kafka.processing.FixedRetryPolicy;
import com.nori.tc.common.kafka.processing.PartitionCommitCoordinator;
import com.nori.tc.common.mailbox.MailboxScheduler;
import com.nori.tc.common.task.execution.MailboxExecutionRuntime;
import com.nori.tc.common.task.policy.DefaultDlqRecordFactory;
import com.nori.tc.common.task.policy.DefaultTaskHandlingPolicy;
import com.nori.tc.common.task.policy.DlqRecord;
import com.nori.tc.common.task.policy.TaskFailureCategory;
import com.nori.tc.common.task.policy.TaskFailureContext;
import com.nori.tc.common.task.policy.TaskHandlingAction;
import com.nori.tc.common.task.policy.TaskHandlingDecision;
import com.nori.tc.common.task.policy.TaskTimeoutExceededException;
import com.nori.tc.common.task.policy.TimeoutBoundRunner;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
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
 * Business Core 런타임 엔진의 메인 오케스트레이터입니다.
 *
 * <p>주요 역할:</p>
 * <p>1) topic consumer loop에서 inbound record를 수집</p>
 * <p>2) mailbox/readyQueue 스케줄러로 eqpId 단위 in-flight=1 보장</p>
 * <p>3) worker에서 timeout/retry/DLQ 정책을 적용하며 task 실행</p>
 *
 * <p>설계 주의사항:</p>
 * <p>- Kafka Consumer API는 thread-safe가 아니므로 poll/ack 처리는 topic consumer loop에서만 수행합니다.</p>
 * <p>- 실제 commit API 호출은 listener/adapter 계층에서 수행하고, 엔진은 commit 가능한 offset 계산까지만 담당합니다.</p>
 */
@Component
public class BusinessRuntimeEngine implements SmartLifecycle, BusinessTaskIngressPort {

    private static final Logger log = LoggerFactory.getLogger(BusinessRuntimeEngine.class);

    private final BusinessCoreRuntimeProperties properties;
    private final BusinessModelRuntimeProvider modelRuntimeProvider;
    private final BusinessUiTaskExecutor uiTaskExecutor;
    private final BusinessWorkflowMatcher workflowMatcher;
    private final BusinessWorkflowActionExecutor workflowActionExecutor;
    private final BusinessDlqPublisherPort dlqPublisherPort;
    private final MailboxScheduler<BusinessMailboxTask> mailboxScheduler;
    private final DefaultTaskHandlingPolicy taskHandlingPolicy;
    private final BusinessRuntimeDispositionMetrics dispositionMetrics;
    private final MailboxExecutionRuntime<BusinessMailboxTask> mailboxExecutionRuntime;

    private final Map<String, TopicRuntime> topicRuntimes = new java.util.concurrent.ConcurrentHashMap<>();

    private ScheduledExecutorService timeoutScheduler;

    private volatile boolean running = false;

    /**
     * 런타임 엔진 의존성을 주입받습니다.
     *
     * @param properties runtime 프로퍼티
     * @param modelRuntimeProvider eqpId -> model runtime provider
     * @param uiTaskExecutor UI task 실행기
     * @param workflowMatcher non-UI workflow 매처
     * @param workflowActionExecutor non-UI action 실행기
     * @param dlqPublisherPort DLQ 발행 포트
     * @param dispositionMetricsProvider disposition 집계기 provider
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
     * 테스트/세부 제어용 생성자입니다.
     *
     * <p>DLQ 포트와 disposition 계측기를 명시적으로 주입해
     * 실패/재시도/이관 시나리오를 정밀 검증할 때 사용합니다.</p>
     *
     * @param properties runtime 프로퍼티
     * @param modelRuntimeProvider eqpId -> model runtime provider
     * @param uiTaskExecutor UI task 실행기
     * @param workflowMatcher non-UI workflow 매처
     * @param workflowActionExecutor non-UI action 실행기
     * @param dlqPublisherPort DLQ 발행 포트
     * @param dispositionMetrics disposition 집계기
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
        this.taskHandlingPolicy = new DefaultTaskHandlingPolicy(
                new FixedRetryPolicy(
                        properties.getRuntime().getRetryMaxAttempts(),
                        properties.getRuntime().getRetryBackoffMs()
                ),
                new DefaultDlqRecordFactory(300),
                true
        );
        this.mailboxExecutionRuntime = createMailboxExecutionRuntime();
    }

    /**
     * 기존 테스트/호출 코드 호환을 위한 생성자입니다.
     *
     * <p>DLQ 포트를 명시하지 않은 경우 no-op 포트를 사용합니다.</p>
     *
     * @param properties runtime 프로퍼티
     * @param modelRuntimeProvider eqpId -> model runtime provider
     * @param uiTaskExecutor UI task 실행기
     * @param workflowMatcher non-UI workflow 매처
     * @param workflowActionExecutor non-UI action 실행기
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
     * 테스트/골격 단계에서 사용할 간소 생성자입니다.
     *
     * @param properties runtime properties
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
     * 런타임 엔진을 중지합니다.
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
     * 런타임 실행 상태를 반환합니다.
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Spring lifecycle phase를 반환합니다.
     */
    @Override
    public int getPhase() {
        return 0;
    }

    /**
     * 외부 ingress 포트에서 inbound record를 받아 topic별 inbound queue에 적재합니다.
     *
     * @param record inbound record
     * @return enqueue 성공 여부
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
     * 소비 대상 topic runtime을 초기화합니다.
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
     * topic consumer loop를 시작합니다.
     */
    private void startTopicConsumers() {
        for (TopicRuntime topicRuntime : topicRuntimes.values()) {
            topicRuntime.consumerPool().execute(() -> runTopicConsumerLoop(topicRuntime));
        }
    }

    /**
     * dispatcher loop를 시작합니다.
     */
    /**
     * 공통 mailbox 실행 런타임을 생성합니다.
     *
     * <p>dispatcher/worker 루프 자체는 공통 모듈로 위임하고,</p>
     * <p>비즈니스 엔진은 task 처리 정책만 유지합니다.</p>
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
                ex -> log.error("Business mailbox dispatcher 루프에서 예외가 발생했습니다.", ex)
        );
    }

    /**
     * topic consumer loop입니다.
     *
     * <p>동작 순서:</p>
     * <p>1) ackQueue를 drain하여 commit tracker 업데이트</p>
     * <p>2) inbound queue에서 record를 poll</p>
     * <p>3) mailbox 스케줄러에 task enqueue</p>
     */
    private void runTopicConsumerLoop(final TopicRuntime topicRuntime) {
        while (running) {
            try {
                drainAckAndPrepareCommit(topicRuntime);

                final BusinessInboundRecord record = topicRuntime.inboundQueue().poll(200, TimeUnit.MILLISECONDS);
                if (record == null) {
                    continue;
                }

                final TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
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
     * worker에서 전달된 ack를 drain하고 commit 가능한 offset 범위를 계산합니다.
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

        final Map<TopicPartition, OffsetAndMetadata> commitOffsets = topicRuntime.commitCoordinator().collectCommitOffsets();
        if (!commitOffsets.isEmpty() && log.isDebugEnabled()) {
            log.debug("Prepared commit offsets. topic={}, offsets={}", topicRuntime.topicName(), commitOffsets);
        }
    }

    /**
     * dispatcher loop입니다.
     *
     * <p>readyQueue에서 eqpId를 꺼낸 뒤 mailbox in-flight CAS를 획득하고,
     * workerPool에 단일 task를 위임합니다.</p>
     */
    /**
     * worker 큐에서 task 거부가 발생했을 때 DLQ 전환 처리를 수행합니다.
     *
     * @param task 거부된 task
     * @param rejected 거부 예외
     */
    private void handleWorkerRejectedTask(
            final BusinessMailboxTask task,
            final RejectedExecutionException rejected
    ) {
        log.error("Worker pool rejected task. eqpId={}, topic={}, partition={}, offset={}",
                task.record().eqpId(),
                task.record().topic(),
                task.record().partition(),
                task.record().offset(),
                rejected);
        recordDisposition(task.record(), BusinessRuntimeDisposition.DLQ, "WORKER_POOL_REJECTED");
        emitAck(task.record(), AckStatus.DLQ);
    }

    /**
     * worker에서 단일 task를 처리합니다.
     *
     * <p>종료 처리 규칙:</p>
     * <p>- 성공/정상 미매칭: SUCCESS ack</p>
     * <p>- timeout: TIMEOUT 카테고리로 retry/DLQ 판단</p>
     * <p>- 필터/액션 예외: FILTER_EVAL/ACTION_EXEC 카테고리로 retry/DLQ 판단</p>
     */
    /**
     * 단일 business task를 처리합니다.
     *
     * <p>정상 처리와 실패 처리(재시도/ DLQ/거부)는 기존 정책을 그대로 유지합니다.</p>
     *
     * @param task 처리할 business task
     */
    private void processTask(final BusinessMailboxTask task) {
        try {
            final TaskExecutionOutcome outcome = executeWithTimeout(task);
            if (outcome == TaskExecutionOutcome.WORKFLOW_NOT_FOUND) {
                log.info("No workflow matched. topic={}, eqpId={}, messageName={}, partition={}, offset={}",
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

    /**
     * timeout 감시를 포함해 task를 실행합니다.
     */
    private TaskExecutionOutcome executeWithTimeout(final BusinessMailboxTask task) throws Exception {
        final TimeoutBoundRunner timeoutBoundRunner = new TimeoutBoundRunner(
                timeoutScheduler,
                properties.getRuntime().getTaskTimeoutMs()
        );
        return timeoutBoundRunner.run(() -> executeTask(task));
    }

    /**
     * task 본문을 실행합니다.
     *
     * <p>분기 규칙:</p>
     * <p>- UI: 공통 UI pipeline 위임</p>
     * <p>- EQP/MES: 모델 런타임 조회 -> workflow 매칭 -> action 실행</p>
     */
    private TaskExecutionOutcome executeTask(final BusinessMailboxTask task) {
        if (task.record().messageType() == BusinessMessageType.UI) {
            executeUiTask(task);
            return TaskExecutionOutcome.SUCCESS;
        }

        return executeNonUiTask(task);
    }

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
     * 실패 카테고리와 정책 결정 결과를 기준으로 retry/DLQ/continue를 처리합니다.
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
     * retry task를 timeout scheduler에 등록합니다.
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
     * runtime DLQ 이벤트를 외부 DLQ sink 포트로 발행합니다.
     *
     * <p>핵심 원칙:</p>
     * <p>- runtime 처리는 DLQ 저장 실패 때문에 중단되지 않아야 하므로
     * 본 메서드는 예외를 외부로 전파하지 않고 내부 로그로 흡수합니다.</p>
     *
     * @param task 실패한 task
     * @param decision 실패 정책 결정 결과
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
                null,
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
     * DLQ reasonMessage를 빈 값 없이 보정합니다.
     *
     * @param message 후보 reasonMessage
     * @param fallback 기본 reasonMessage
     * @return 정규화된 reasonMessage
     */
    private static String resolveReasonMessage(final String message, final String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message.trim();
    }

    /**
     * topic별 ackQueue에 ack 이벤트를 적재합니다.
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
     * disposition 집계 및 운영 로그를 기록합니다.
     *
     * <p>로그 레벨 정책:</p>
     * <p>1) ACCEPTED는 트래픽량이 높아 debug로 기록</p>
     * <p>2) RETRY/DLQ/REJECTED는 운영 추적 핵심 이벤트이므로 info로 기록</p>
     *
     * @param record 대상 레코드
     * @param disposition 표준 disposition
     * @param reason 결과 사유 코드
     */
    private void recordDisposition(
            final BusinessInboundRecord record,
            final BusinessRuntimeDisposition disposition,
            final String reason
    ) {
        dispositionMetrics.increment(disposition);
        if (disposition == BusinessRuntimeDisposition.ACCEPTED) {
            if (log.isDebugEnabled()) {
                log.debug("BUSINESS_TASK_DISPOSITION. disposition={}, reason={}, topic={}, eqpId={}, partition={}, offset={}, messageName={}",
                        disposition,
                        reason,
                        record.topic(),
                        record.eqpId(),
                        record.partition(),
                        record.offset(),
                        record.messageName());
            }
            return;
        }

        log.info("BUSINESS_TASK_DISPOSITION. disposition={}, reason={}, topic={}, eqpId={}, partition={}, offset={}, messageName={}",
                disposition,
                reason,
                record.topic(),
                record.eqpId(),
                record.partition(),
                record.offset(),
                record.messageName());
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    /**
     * 엔진 내부 스레드 이름 접두사를 부여하는 factory입니다.
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
     * Executor를 즉시 종료(shutdownNow)하고 종료 대기를 수행합니다.
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
     * topic 단위 consumer runtime 묶음입니다.
     *
     * @param topicName topic 이름
     * @param messageType message 타입(EQP/MES/UI)
     * @param inboundQueue topic inbound queue
     * @param ackQueue topic ack queue
     * @param commitCoordinator topic commit tracker
     * @param ackDrainMaxBatch ack drain 최대 개수
     * @param consumerPool topic consumer thread pool
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
     * task 실행 결과 타입입니다.
     */
    private enum TaskExecutionOutcome {
        SUCCESS,
        WORKFLOW_NOT_FOUND
    }
}


