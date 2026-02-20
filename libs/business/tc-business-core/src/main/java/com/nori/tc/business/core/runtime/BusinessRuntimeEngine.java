package com.nori.tc.business.core.runtime;

import com.nori.tc.business.core.config.BusinessCoreRuntimeProperties;
import com.nori.tc.business.core.dlq.BusinessDlqPublisherPort;
import com.nori.tc.business.core.logging.BusinessLogContext;
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
import com.nori.tc.common.kafka.processing.AckEvent;
import com.nori.tc.common.kafka.processing.AckQueue;
import com.nori.tc.common.kafka.processing.AckStatus;
import com.nori.tc.common.kafka.processing.FixedRetryPolicy;
import com.nori.tc.common.kafka.processing.PartitionCommitCoordinator;
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
 * BusinessRuntimeEngine ?대옒?ㅼ엯?덈떎.
 *
 * <p>?대떦 紐⑤뱢?먯꽌 怨듯넻 怨꾩빟怨??숈옉 寃쎄퀎瑜??뺤쓽?섎ŉ,
 * ?몄텧 怨꾩링?먯꽌 ?쇨????ъ슜??媛?ν븯?꾨줉 ?ㅺ퀎?섏뿀?듬땲??</p>
 */
@Component
public class BusinessRuntimeEngine implements SmartLifecycle, BusinessTaskIngressPort {

    private static final Logger log = LoggerFactory.getLogger(BusinessRuntimeEngine.class);
    /**
     * TRACE_ID_NOT_AVAILABLE ?꾨뱶?낅땲??
     */
    private static final String TRACE_ID_NOT_AVAILABLE = "N/A";

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
     * UTF-8 ?뺤떇?쇰줈 ?뺣━??二쇱꽍?낅땲??
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
     * UTF-8 ?뺤떇?쇰줈 ?뺣━??二쇱꽍?낅땲??
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
     * UTF-8 ?뺤떇?쇰줈 ?뺣━??二쇱꽍?낅땲??
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
     * UTF-8 ?뺤떇?쇰줈 ?뺣━??二쇱꽍?낅땲??
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
     * start 湲곕뒫???섑뻾?⑸땲??
     *
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
     * stop 湲곕뒫???섑뻾?⑸땲??
     *
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
     * isRunning 湲곕뒫???섑뻾?⑸땲??
     *
     * @return 泥섎━ 寃곌낵
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * getPhase 湲곕뒫???섑뻾?⑸땲??
     *
     * @return 泥섎━ 寃곌낵
     */
    @Override
    public int getPhase() {
        return 0;
    }

    /**
     * submit 湲곕뒫???섑뻾?⑸땲??
     *
     * @param record ?낅젰 媛?     * @return 泥섎━ 寃곌낵
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
     * registerTopicRuntimes 湲곕뒫???섑뻾?⑸땲??
     *
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
     * startTopicConsumers 湲곕뒫???섑뻾?⑸땲??
     *
     */
    private void startTopicConsumers() {
        for (TopicRuntime topicRuntime : topicRuntimes.values()) {
            topicRuntime.consumerPool().execute(() -> runTopicConsumerLoop(topicRuntime));
        }
    }

    /**
     * UTF-8 ?뺤떇?쇰줈 ?뺣━??二쇱꽍?낅땲??
     */
    /**
     * createMailboxExecutionRuntime 湲곕뒫???섑뻾?⑸땲??
     *
     * @return 泥섎━ 寃곌낵
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
                (task, ex) -> log.error("Business task 嶺뚳퐣瑗??繞????깅뇶?띠럾? ?꾩룇裕뉑틦???곕????덈펲. topic={}, eqpId={}, partition={}, offset={}",
                        task.record().topic(),
                        task.record().eqpId(),
                        task.record().partition(),
                        task.record().offset(),
                        ex),
                ex -> log.error("Business mailbox dispatcher ?猷먮쳜?????????깅뇶?띠럾? ?꾩룇裕뉑틦???곕????덈펲.", ex)
        );
    }

    /**
     * runTopicConsumerLoop 湲곕뒫???섑뻾?⑸땲??
     *
     * @param topicRuntime ?낅젰 媛?     */
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
     * drainAckAndPrepareCommit 湲곕뒫???섑뻾?⑸땲??
     *
     * @param topicRuntime ?낅젰 媛?     */
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
     * UTF-8 ?뺤떇?쇰줈 ?뺣━??二쇱꽍?낅땲??
     */
    /**
     * UTF-8 ?뺤떇?쇰줈 ?뺣━??二쇱꽍?낅땲??
     */
    private void handleWorkerRejectedTask(
            final BusinessMailboxTask task,
            final RejectedExecutionException rejected
    ) {
        /*
         * mailbox worker 嫄곗젅 ?쒖젏? 鍮꾨룞湲?寃쎄퀎?대ŉ, ?몄텧 ?ㅻ젅??MDC瑜??좊ː?????놁뒿?덈떎.
         * ?곕씪??task???닿릿 eqpId瑜?湲곗??쇰줈 MDC瑜??ъ＜?낇븯???ㅻ퉬 濡쒓렇 ?뚯씪 遺꾨━瑜?蹂댁옣?⑸땲??
         */
        try (BusinessLogContext ignored = BusinessLogContext.withEqpId(task.record().eqpId())) {
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
     * UTF-8 ?뺤떇?쇰줈 ?뺣━??二쇱꽍?낅땲??
     */
    /**
     * processTask 湲곕뒫???섑뻾?⑸땲??
     *
     * @param task ?낅젰 媛?     */
    private void processTask(final BusinessMailboxTask task) {
        /*
         * worker 吏꾩엯 ?쒖젏??eqpId MDC瑜?二쇱엯??
         * task 泥섎━ ?꾩껜 濡쒓렇(?깃났/?ㅽ뙣/?ъ떆??遺꾧린)瑜??숈씪 ?ㅻ퉬 濡쒓렇 ?뚯씪濡?蹂대깄?덈떎.
         */
        try (BusinessLogContext ignored = BusinessLogContext.withEqpId(task.record().eqpId())) {
            if (log.isDebugEnabled()) {
                log.debug("Business task processing started. topic={}, eqpId={}, partition={}, offset={}, messageType={}, messageName={}",
                        task.record().topic(),
                        task.record().eqpId(),
                        task.record().partition(),
                        task.record().offset(),
                        task.record().messageType(),
                        task.record().messageName());
            }

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
    }

    /**
     * executeWithTimeout 湲곕뒫???섑뻾?⑸땲??
     *
     * @param task ?낅젰 媛?     * @return 泥섎━ 寃곌낵
     */
    private TaskExecutionOutcome executeWithTimeout(final BusinessMailboxTask task) throws Exception {
        final TimeoutBoundRunner timeoutBoundRunner = new TimeoutBoundRunner(
                timeoutScheduler,
                properties.getRuntime().getTaskTimeoutMs()
        );
        return timeoutBoundRunner.run(() -> executeTask(task));
    }

    /**
     * executeTask 湲곕뒫???섑뻾?⑸땲??
     *
     * @param task ?낅젰 媛?     * @return 泥섎━ 寃곌낵
     */
    private TaskExecutionOutcome executeTask(final BusinessMailboxTask task) {
        if (task.record().messageType() == BusinessMessageType.UI) {
            executeUiTask(task);
            return TaskExecutionOutcome.SUCCESS;
        }

        return executeNonUiTask(task);
    }

    /**
     * executeUiTask 湲곕뒫???섑뻾?⑸땲??
     *
     * @param task ?낅젰 媛?     */

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
     * executeNonUiTask 湲곕뒫???섑뻾?⑸땲??
     *
     * @param task ?낅젰 媛?     * @return 泥섎━ 寃곌낵
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
     * UTF-8 ?뺤떇?쇰줈 ?뺣━??二쇱꽍?낅땲??
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
     * scheduleRetry 湲곕뒫???섑뻾?⑸땲??
     *
     * @param task ?낅젰 媛?     * @param backoffMs ?낅젰 媛?     */
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
     * publishRuntimeDlq 湲곕뒫???섑뻾?⑸땲??
     *
     * @param task ?낅젰 媛?     * @param decision ?낅젰 媛?     */
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
     * resolveReasonMessage 湲곕뒫???섑뻾?⑸땲??
     *
     * @param message ?낅젰 媛?     * @param fallback ?낅젰 媛?     * @return 泥섎━ 寃곌낵
     */
    private static String resolveReasonMessage(final String message, final String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message.trim();
    }

    /**
     * emitAck 湲곕뒫???섑뻾?⑸땲??
     *
     * @param record ?낅젰 媛?     * @param status ?낅젰 媛?     */
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
     * UTF-8 ?뺤떇?쇰줈 ?뺣━??二쇱꽍?낅땲??
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
                        TRACE_ID_NOT_AVAILABLE,
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
                TRACE_ID_NOT_AVAILABLE,
                record.messageName());
    }

    /**
     * resolveDispositionFlow 湲곕뒫???섑뻾?⑸땲??
     *
     * @param record ?낅젰 媛?     * @return 泥섎━ 寃곌낵
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
     * now 湲곕뒫???섑뻾?⑸땲??
     *
     * @return 泥섎━ 寃곌낵
     */

    private static long now() {
        return System.currentTimeMillis();
    }

    /**
     * namedThreadFactory 湲곕뒫???섑뻾?⑸땲??
     *
     * @param prefix ?낅젰 媛?     * @return 泥섎━ 寃곌낵
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
     * shutdownExecutor 湲곕뒫???섑뻾?⑸땲??
     *
     * @param executor ?낅젰 媛?     * @param name ?낅젰 媛?     */
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
     * UTF-8 ?뺤떇?쇰줈 ?뺣━??二쇱꽍?낅땲??
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
     * UTF-8 ?뺤떇?쇰줈 ?뺣━??二쇱꽍?낅땲??
     */
    private enum TaskExecutionOutcome {
        SUCCESS,
        WORKFLOW_NOT_FOUND
    }
}



