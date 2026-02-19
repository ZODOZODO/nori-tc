package com.nori.tc.messaging.kafka.starter.runtime;

import com.nori.tc.common.kafka.processing.AckEvent;
import com.nori.tc.common.kafka.processing.AckQueue;
import com.nori.tc.common.kafka.processing.AckStatus;
import com.nori.tc.common.kafka.processing.FixedRetryPolicy;
import com.nori.tc.common.kafka.processing.PartitionCommitCoordinator;
import com.nori.tc.common.kafka.processing.RetryDecision;
import com.nori.tc.common.kafka.processing.RetryPolicy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka Consumer 怨듯넻 ?쇱씠?꾩궗?댄겢 ?쒗뵆由우엯?덈떎.
 *
 * <p>以묐났?섎뒗 ?ㅽ뻾 ?먮쫫(?쒖옉/醫낅즺, poll, commit retry, lag sampling)?? * ??怨녹뿉??愿由ы븯怨? ?ㅼ젣 ?덉퐫??泥섎━留??섏쐞 ?대옒?ㅺ? 援ы쁽?섎룄濡??ㅺ퀎?덉뒿?덈떎.</p>
 *
 * <p>Step ?듯빀 ?ы빆:</p>
 * <p>- commit ?ъ떆?꾨뒗 {@code tc-common-kafka-consumer-runtime}??{@link RetryPolicy}瑜??ъ슜?⑸땲??</p>
 * <p>- ?뺤콉 怨꾩궛怨??ㅽ뻾 猷⑦봽瑜?遺꾨━???깅퀎 consumer?먯꽌 ?숈씪???ъ떆???섎?瑜?怨듭쑀?⑸땲??</p>
 *
 * @param <T> Consumer value ??? */
public abstract class AbstractKafkaConsumerLifecycle<T> implements Runnable, SmartLifecycle {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private volatile boolean running = false;
    private volatile long lastLagSampleAt = 0L;
    private KafkaConsumer<String, T> consumer;
    private Thread workerThread;

    /**
     * KafkaConsumer ?앹꽦???ъ슜???꾨줈?쇳떚瑜?諛섑솚?⑸땲??
     */
    protected abstract Map<String, Object> consumerProperties();

    /**
     * Consumer 諛붿씤??紐⑤뱶瑜?諛섑솚?⑸땲??
     */
    protected abstract KafkaConsumerBindingMode bindingMode();

    /**
     * SUBSCRIBE 紐⑤뱶????援щ룆??topic 紐⑸줉??諛섑솚?⑸땲??
     */
    protected List<String> subscribeTopics() {
        return List.of();
    }

    /**
     * ASSIGN 紐⑤뱶?????좊떦??partition 紐⑸줉??諛섑솚?⑸땲??
     */
    protected List<TopicPartition> assignedPartitions() {
        return List.of();
    }

    /**
     * poll timeout 媛믪쓣 諛섑솚?⑸땲??
     */
    protected abstract Duration pollTimeout();

    /**
     * Consumer worker thread ?대쫫??諛섑솚?⑸땲??
     */
    protected abstract String threadName();

    /**
     * 醫낅즺 ??worker join ?湲??쒓컙??諛섑솚?⑸땲??
     */
    protected abstract long shutdownWaitMs();

    /**
     * commit ?ъ떆??理쒕? ?잛닔瑜?諛섑솚?⑸땲??
     */
    protected abstract int commitRetryMax();

    /**
     * commit ?ъ떆??backoff(ms)瑜?諛섑솚?⑸땲??
     */
    protected abstract long commitRetryBackoffMs();

    /**
     * lag ?섑뵆留?二쇨린(ms)瑜?諛섑솚?⑸땲??
     */
    protected abstract long lagSampleIntervalMs();

    /**
     * poll ?ㅻ젅?쒖? ?덉퐫??泥섎━ ?ㅻ젅?쒕? 遺꾨━?좎? ?щ?瑜?諛섑솚?⑸땲??
     *
     * <p>true??寃쎌슦 poll ?ㅻ젅?쒕뒗 Kafka I/O, ack ?쒕젅?? 而ㅻ컠 泥섎━??吏묒쨷?섍퀬,
     * ?ㅼ젣 ?덉퐫??鍮꾩쫰?덉뒪 泥섎━??蹂꾨룄 worker ?ㅻ젅??????꾩엫?⑸땲??</p>
     */
    protected boolean asyncRecordProcessingEnabled() {
        return false;
    }

    /**
     * 鍮꾨룞湲?泥섎━ 紐⑤뱶?먯꽌 ?ъ슜??worker ?ㅻ젅???섎? 諛섑솚?⑸땲??
     */
    protected int recordWorkerThreads() {
        return 1;
    }

    /**
     * poll 猷⑦봽媛 ??踰덉뿉 ?쒕젅?명븷 ack ?대깽??理쒕? 媛쒖닔瑜?諛섑솚?⑸땲??
     */
    protected int ackDrainMaxBatch() {
        return 512;
    }

    /**
     * 鍮꾨룞湲?泥섎━ 紐⑤뱶?먯꽌 ?덉슜??理쒕? in-flight ?덉퐫???섎? 諛섑솚?⑸땲??
     */
    protected int maxInFlightRecords() {
        return 10_000;
    }

    /**
     * poll???⑥씪 ?덉퐫?쒕? 泥섎━?⑸땲??
     */
    protected abstract void handleRecord(ConsumerRecord<String, T> record);

    /**
     * 濡쒓렇 異쒕젰??consumer ?앸퀎紐낆쓣 諛섑솚?⑸땲??
     */
    protected String consumerName() {
        return getClass().getSimpleName();
    }

    /**
     * commit ?ㅽ뙣 ???몄텧?섎뒗 ?낆엯?덈떎.
     */
    protected void onCommitFail(final Exception ex, final int attempt) {
        log.warn("Kafka commit failed. consumer={}, attempt={}", consumerName(), attempt, ex);
    }

    /**
     * ?덉퐫??泥섎━ ?ㅽ뙣 ???몄텧?섎뒗 ?낆엯?덈떎.
     */
    protected void onRecordFail(final ConsumerRecord<String, T> record, final Exception ex) {
        log.warn("Kafka record handling failed. consumer={}, topic={}, partition={}, offset={}",
                consumerName(), record.topic(), record.partition(), record.offset(), ex);
    }

    /**
     * lag ?섑뵆 吏?먮쭏???몄텧?섎뒗 ?낆엯?덈떎.
     */
    protected void onLagSample(final TopicPartition topicPartition, final long lag) {
        // no-op
    }

    /**
     * Consumer ?앹꽦 諛?諛붿씤?⑹씠 ?앸궃 ???몄텧?섎뒗 ?낆엯?덈떎.
     */
    protected void afterStart(final KafkaConsumer<String, T> startedConsumer) {
        // no-op
    }

    /**
     * ?덉퐫??泥섎━ ?ㅽ뙣媛 諛쒖깮?덉쓣 ???대떦 poll 諛곗튂瑜?而ㅻ컠?좎? ?щ?瑜?諛섑솚?⑸땲??
     *
     * <p>true硫?湲곗〈 ?숈옉(?ㅽ뙣媛 ?덉뼱??commit ?쒕룄)???좎??섍퀬,
     * false硫??대떦 諛곗튂 commit??嫄대꼫?곌퀬 ?ъ쿂由?寃쎈줈濡?蹂대깄?덈떎.</p>
     */
    protected boolean commitOnRecordFailure() {
        return true;
    }

    /**
     * ?덉퐫???ㅽ뙣 ???ㅽ뙣??offset?쇰줈 seek ?섏뿬 ?ъ떆?꾪븷吏 ?щ?瑜?諛섑솚?⑸땲??
     *
     * <p>commitOnRecordFailure=false? ?④퍡 ?ъ슜?댁빞 ?섎?媛 ?덉뒿?덈떎.</p>
     */
    protected boolean retryFailedRecordFromCurrentOffset() {
        return false;
    }

    /**
     * ?ㅽ뙣 ?덉퐫???ъ떆?????湲??쒓컙(ms)??諛섑솚?⑸땲??
     */
    protected long failedRecordRetryBackoffMs() {
        return 0L;
    }

    /**
     * Consumer 猷⑦봽瑜??쒖옉?⑸땲??
     */
    @Override
    public synchronized void start() {
        if (running) {
            if (log.isDebugEnabled()) {
                log.debug("Kafka consumer already running. consumer={}", consumerName());
            }
            return;
        }
        log.info("Kafka consumer starting. consumer={}, mode={}", consumerName(), bindingMode());
        running = true;

        final KafkaConsumer<String, T> createdConsumer = new KafkaConsumer<>(consumerProperties());
        bindConsumer(createdConsumer);
        consumer = createdConsumer;

        workerThread = new Thread(this, threadName());
        workerThread.setDaemon(true);
        workerThread.start();

        afterStart(createdConsumer);
        log.info("Kafka consumer started. consumer={}, thread={}", consumerName(), workerThread.getName());
    }

    /**
     * Consumer 猷⑦봽瑜?醫낅즺?⑸땲??
     */
    @Override
    public synchronized void stop() {
        running = false;
        log.info("Kafka consumer stopping. consumer={}", consumerName());

        final KafkaConsumer<String, T> current = snapshotConsumer();
        if (current != null) {
            current.wakeup();
        }

        final Thread currentWorker = workerThread;
        if (currentWorker != null) {
            try {
                currentWorker.join(shutdownWaitMs());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        closeConsumerQuietly();
        log.info("Kafka consumer stopped. consumer={}", consumerName());
    }

    /**
     * ?꾩옱 援щ룞 ?щ?瑜?諛섑솚?⑸땲??
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * SmartLifecycle phase瑜?諛섑솚?⑸땲??
     */
    @Override
    public int getPhase() {
        return 0;
    }

    /**
     * ?ㅼ젣 poll-processing 猷⑦봽瑜??섑뻾?⑸땲??
     */
    @Override
    public void run() {
        final KafkaConsumer<String, T> runningConsumer = snapshotConsumer();
        if (runningConsumer == null) {
            log.debug("Kafka consumer loop skipped (consumer is null). consumer={}", consumerName());
            return;
        }

        final AsyncProcessingContext asyncContext = initializeAsyncContextIfEnabled();

        try {
            while (running) {
                if (asyncContext != null) {
                    syncAssignmentTrackers(runningConsumer, asyncContext);
                    applyBackpressure(runningConsumer, asyncContext);
                }

                final ConsumerRecords<String, T> records = runningConsumer.poll(pollTimeout());
                if (!records.isEmpty() && log.isDebugEnabled()) {
                    log.debug("Kafka records polled. consumer={}, count={}", consumerName(), records.count());
                }

                if (asyncContext == null) {
                    handleRecordsSynchronously(runningConsumer, records);
                } else {
                    submitRecordsAsync(records, asyncContext);
                    drainAckAndCommit(runningConsumer, asyncContext);
                    if (records.isEmpty()) {
                        // poll 寃곌낵媛 鍮꾩뼱??worker ack???꾩쟻?????덉쑝誘濡?二쇨린?곸쑝濡??쒕젅?명빀?덈떎.
                        drainAckAndCommit(runningConsumer, asyncContext);
                    }
                }

                if (!records.isEmpty() || asyncContext != null) {
                    sampleLagIfNeeded(runningConsumer);
                }
            }
        } catch (WakeupException ignored) {
            log.debug("Kafka consumer wakeup received. consumer={}", consumerName());
        } finally {
            if (asyncContext != null) {
                flushAsyncContextOnShutdown(runningConsumer, asyncContext);
                shutdownAsyncWorker(asyncContext);
            }
            closeConsumerQuietly();
        }
    }

    /**
     * 湲곗〈 ?숆린 泥섎━ 紐⑤뱶??poll 諛곗튂 泥섎━/而ㅻ컠 ?먮쫫???섑뻾?⑸땲??
     */
    private void handleRecordsSynchronously(
            final KafkaConsumer<String, T> runningConsumer,
            final ConsumerRecords<String, T> records
    ) {
        if (records.isEmpty()) {
            return;
        }

        boolean recordFailed = false;
        ConsumerRecord<String, T> failedRecord = null;
        for (ConsumerRecord<String, T> record : records) {
            try {
                handleRecord(record);
            } catch (Exception ex) {
                onRecordFail(record, ex);
                recordFailed = true;
                failedRecord = record;
                if (!commitOnRecordFailure()) {
                    break;
                }
            }
        }

        if (recordFailed && !commitOnRecordFailure()) {
            handleFailedRecordRetry(runningConsumer, failedRecord);
            return;
        }

        commitWithRetry(runningConsumer, Map.of());
    }

    /**
     * 鍮꾨룞湲?泥섎━ 紐⑤뱶 ?ㅼ젙???쒖꽦?붾릺硫?worker/ack/commit ?곹깭瑜?珥덇린?뷀빀?덈떎.
     */
    private AsyncProcessingContext initializeAsyncContextIfEnabled() {
        if (!asyncRecordProcessingEnabled()) {
            return null;
        }

        final int workerThreads = Math.max(1, recordWorkerThreads());
        final int drainMaxBatch = Math.max(1, ackDrainMaxBatch());
        final int inFlightLimit = Math.max(1, maxInFlightRecords());

        if (!commitOnRecordFailure() && !retryFailedRecordFromCurrentOffset()) {
            log.warn("Async mode enabled with commitOnRecordFailure=false and retryFailedRecordFromCurrentOffset=false. consumer={}, failed records may block contiguous commit.",
                    consumerName());
        }

        final AsyncProcessingContext context = new AsyncProcessingContext(
                Executors.newFixedThreadPool(workerThreads, runnable -> {
                    final Thread thread = new Thread(runnable);
                    thread.setDaemon(true);
                    thread.setName(threadName() + "-worker-" + contextWorkerIndex.incrementAndGet());
                    return thread;
                }),
                drainMaxBatch,
                inFlightLimit
        );

        log.info("Kafka async consumer mode enabled. consumer={}, workerThreads={}, ackDrainMaxBatch={}, maxInFlightRecords={}",
                consumerName(), workerThreads, drainMaxBatch, inFlightLimit);
        return context;
    }

    /**
     * ?꾩옱 assignment 湲곗??쇰줈 而ㅻ컠 ?몃옒而??깅줉/?댁젣瑜??숆린?뷀빀?덈떎.
     */
    private void syncAssignmentTrackers(
            final KafkaConsumer<String, T> runningConsumer,
            final AsyncProcessingContext context
    ) {
        final Set<TopicPartition> currentAssignment = runningConsumer.assignment();
        if (currentAssignment.isEmpty()) {
            return;
        }

        for (TopicPartition topicPartition : currentAssignment) {
            if (!context.knownAssignments.contains(topicPartition)) {
                final long initialOffset = resolvePositionOrZero(runningConsumer, topicPartition);
                context.commitCoordinator.registerPartitionIfAbsent(topicPartition, initialOffset);
                context.knownAssignments.add(topicPartition);
                if (log.isDebugEnabled()) {
                    log.debug("Commit tracker registered from assignment. consumer={}, topicPartition={}, initialOffset={}",
                            consumerName(), topicPartition, initialOffset);
                }
            }
        }

        final List<TopicPartition> removed = new ArrayList<>();
        for (TopicPartition known : context.knownAssignments) {
            if (!currentAssignment.contains(known)) {
                removed.add(known);
            }
        }
        for (TopicPartition topicPartition : removed) {
            context.commitCoordinator.unregisterPartition(topicPartition);
            context.knownAssignments.remove(topicPartition);
            if (log.isDebugEnabled()) {
                log.debug("Commit tracker unregistered from assignment. consumer={}, topicPartition={}",
                        consumerName(), topicPartition);
            }
        }
    }

    /**
     * in-flight 媛쒖닔媛 ?꾧퀎移섎? ?섏쑝硫?assignment瑜?pause?섏뿬 怨쇰룄???곸껜瑜?諛⑹??⑸땲??
     */
    private void applyBackpressure(
            final KafkaConsumer<String, T> runningConsumer,
            final AsyncProcessingContext context
    ) {
        final Set<TopicPartition> assignment = runningConsumer.assignment();
        if (assignment.isEmpty()) {
            return;
        }

        final int inFlight = context.inFlightCount.get();
        if (inFlight >= context.maxInFlightRecords) {
            runningConsumer.pause(assignment);
            if (!context.paused) {
                context.paused = true;
                log.info("Kafka consumer backpressure pause enabled. consumer={}, inFlight={}, maxInFlightRecords={}",
                        consumerName(), inFlight, context.maxInFlightRecords);
            }
            return;
        }

        if (context.paused) {
            runningConsumer.resume(assignment);
            context.paused = false;
            log.info("Kafka consumer backpressure pause released. consumer={}, inFlight={}, maxInFlightRecords={}",
                    consumerName(), inFlight, context.maxInFlightRecords);
        }
    }

    /**
     * poll 諛곗튂 ?덉퐫?쒕? worker ?ㅻ젅?쒖뿉 ?쒖텧?⑸땲??
     */
    private void submitRecordsAsync(final ConsumerRecords<String, T> records, final AsyncProcessingContext context) {
        if (records.isEmpty()) {
            return;
        }

        for (ConsumerRecord<String, T> record : records) {
            final TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
            context.commitCoordinator.registerPartitionIfAbsent(topicPartition, record.offset());
            context.inFlightCount.incrementAndGet();

            try {
                context.workerExecutor.execute(() -> handleRecordAsync(record, context));
            } catch (RuntimeException submitFail) {
                context.inFlightCount.decrementAndGet();
                onRecordFail(record, submitFail);
                offerAck(context, record, commitOnRecordFailure() ? AckStatus.DLQ : AckStatus.FAILED);
            }
        }
    }

    /**
     * worker ?ㅻ젅?쒖뿉???⑥씪 ?덉퐫??泥섎━瑜??섑뻾?섍퀬 寃곌낵瑜?ack ?먯뿉 ?꾨떖?⑸땲??
     */
    private void handleRecordAsync(final ConsumerRecord<String, T> record, final AsyncProcessingContext context) {
        AckStatus ackStatus = AckStatus.SUCCESS;
        try {
            handleRecord(record);
        } catch (Exception ex) {
            onRecordFail(record, ex);
            ackStatus = commitOnRecordFailure() ? AckStatus.DLQ : AckStatus.FAILED;
        } finally {
            offerAck(context, record, ackStatus);
            final int remaining = context.inFlightCount.decrementAndGet();
            if (remaining < 0) {
                context.inFlightCount.set(0);
                log.warn("Kafka async in-flight counter corrected. consumer={}, topic={}, partition={}, offset={}",
                        consumerName(), record.topic(), record.partition(), record.offset());
            }
        }
    }

    /**
     * worker 泥섎━ 寃곌낵瑜?ack ?먯뿉 ?곸옱?⑸땲??
     */
    private void offerAck(
            final AsyncProcessingContext context,
            final ConsumerRecord<String, T> record,
            final AckStatus ackStatus
    ) {
        final AckEvent ackEvent = new AckEvent(
                record.topic(),
                record.partition(),
                record.offset(),
                ackStatus,
                System.currentTimeMillis()
        );
        final boolean offered = context.ackQueue.offer(ackEvent);
        if (!offered) {
            log.warn("Kafka ack enqueue failed (unexpected). consumer={}, topic={}, partition={}, offset={}, status={}",
                    consumerName(), record.topic(), record.partition(), record.offset(), ackStatus);
        }
    }

    /**
     * ack ?먮? ?쒕젅?명븯怨??곗냽 而ㅻ컠 媛?ν븳 ?ㅽ봽?뗭쓣 怨꾩궛??遺遺?而ㅻ컠?⑸땲??
     */
    private void drainAckAndCommit(
            final KafkaConsumer<String, T> runningConsumer,
            final AsyncProcessingContext context
    ) {
        final int drained = context.ackQueue.drainTo(context.ackDrainBuffer, context.ackDrainMaxBatch);
        if (drained <= 0) {
            return;
        }

        for (AckEvent event : context.ackDrainBuffer) {
            if (event.isCommitEligible()) {
                context.commitCoordinator.applyAck(event);
            } else {
                handleNonCommittableAck(runningConsumer, event);
            }
        }

        final Map<TopicPartition, OffsetAndMetadata> commitMap = context.commitCoordinator.collectCommitOffsets();
        if (!commitMap.isEmpty()) {
            commitWithRetry(runningConsumer, commitMap);
        }

        if (log.isDebugEnabled()) {
            log.debug("Kafka async ack drained. consumer={}, drained={}, inFlight={}, pendingAck={}, commitPartitionCount={}",
                    consumerName(),
                    drained,
                    context.inFlightCount.get(),
                    context.ackQueue.size(),
                    commitMap.size());
        }
        context.ackDrainBuffer.clear();
    }

    /**
     * 而ㅻ컠 鍮꾨???ack瑜??뚮퉬 ?ㅻ젅?쒖뿉??蹂댁젙 泥섎━?⑸땲??
     */
    private void handleNonCommittableAck(
            final KafkaConsumer<String, T> runningConsumer,
            final AckEvent event
    ) {
        if (retryFailedRecordFromCurrentOffset()) {
            runningConsumer.seek(event.topicPartition(), event.offset());
            if (log.isDebugEnabled()) {
                log.debug("Kafka consumer seek to failed record from async ack. consumer={}, topic={}, partition={}, offset={}",
                        consumerName(), event.topic(), event.partition(), event.offset());
            }
        }

        final long retryBackoffMs = failedRecordRetryBackoffMs();
        if (retryBackoffMs > 0L) {
            sleepBackoff(retryBackoffMs, "failed record retry");
        }
    }

    /**
     * 醫낅즺 吏곸쟾 ?⑥븘 ?덈뒗 in-flight/ack瑜??쒗븳 ?쒓컙 ?댁뿉 理쒕???鍮꾩썎?덈떎.
     */
    private void flushAsyncContextOnShutdown(
            final KafkaConsumer<String, T> runningConsumer,
            final AsyncProcessingContext context
    ) {
        final long deadline = System.currentTimeMillis() + Math.max(0L, shutdownWaitMs());

        while (context.inFlightCount.get() > 0 || context.ackQueue.size() > 0) {
            drainAckAndCommit(runningConsumer, context);
            if (System.currentTimeMillis() >= deadline) {
                log.info("Kafka async shutdown flush timed out. consumer={}, inFlight={}, pendingAck={}",
                        consumerName(), context.inFlightCount.get(), context.ackQueue.size());
                return;
            }
            if (!sleepBackoff(10L, "async shutdown flush")) {
                return;
            }
        }
        // 理쒖쥌 ?쒕젅?몄쓣 ??踰????섑뻾???꾨씫 而ㅻ컠 媛?μ꽦??以꾩엯?덈떎.
        drainAckAndCommit(runningConsumer, context);
    }

    /**
     * 鍮꾨룞湲?worker ?ㅻ젅??????덉쟾?섍쾶 醫낅즺?⑸땲??
     */
    private void shutdownAsyncWorker(final AsyncProcessingContext context) {
        context.workerExecutor.shutdown();
        try {
            final boolean terminated = context.workerExecutor.awaitTermination(
                    Math.max(1L, shutdownWaitMs()),
                    TimeUnit.MILLISECONDS
            );
            if (!terminated) {
                context.workerExecutor.shutdownNow();
                log.info("Kafka async worker forced shutdown. consumer={}", consumerName());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            context.workerExecutor.shutdownNow();
        }
    }

    /**
     * ?뱀젙 ?뚰떚?섏쓽 ?꾩옱 position??議고쉶?섍퀬 ?ㅽ뙣 ??0??諛섑솚?⑸땲??
     */
    private long resolvePositionOrZero(
            final KafkaConsumer<String, T> runningConsumer,
            final TopicPartition topicPartition
    ) {
        try {
            return Math.max(0L, runningConsumer.position(topicPartition));
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Kafka consumer position read failed. consumer={}, topicPartition={}, fallback=0",
                        consumerName(), topicPartition, ex);
            }
            return 0L;
        }
    }

    /**
     * 紐⑤뱶???곕씪 subscribe/assign 諛붿씤?⑹쓣 ?섑뻾?⑸땲??
     */
    private void bindConsumer(final KafkaConsumer<String, T> createdConsumer) {
        if (bindingMode() == KafkaConsumerBindingMode.SUBSCRIBE) {
            final List<String> topics = subscribeTopics();
            if (topics == null || topics.isEmpty()) {
                throw new IllegalStateException("subscribeTopics must not be empty in SUBSCRIBE mode");
            }
            createdConsumer.subscribe(topics);
            if (log.isDebugEnabled()) {
                log.debug("Kafka consumer subscribed. consumer={}, topics={}", consumerName(), topics);
            }
            return;
        }

        final List<TopicPartition> partitions = assignedPartitions();
        if (partitions == null || partitions.isEmpty()) {
            throw new IllegalStateException("assignedPartitions must not be empty in ASSIGN mode");
        }
        createdConsumer.assign(partitions);
        if (!createdConsumer.subscription().isEmpty()) {
            throw new IllegalStateException("ASSIGN mode consumer must not have subscriptions");
        }
        if (log.isDebugEnabled()) {
            log.debug("Kafka consumer assigned. consumer={}, partitions={}", consumerName(), partitions);
        }
    }

    /**
     * 諛곗튂 commit??retry ?뺤콉???곕씪 ?섑뻾?⑸땲??
     */
    private void commitWithRetry(
            final KafkaConsumer<String, T> runningConsumer,
            final Map<TopicPartition, OffsetAndMetadata> commitMap
    ) {
        final RetryPolicy retryPolicy = createCommitRetryPolicy();
        int failedAttempt = 0;

        while (true) {
            try {
                if (commitMap == null || commitMap.isEmpty()) {
                    runningConsumer.commitSync();
                } else {
                    runningConsumer.commitSync(commitMap);
                }
                if (log.isDebugEnabled()) {
                    log.debug("Kafka commit success. consumer={}, mode={}, partitionCount={}",
                            consumerName(),
                            (commitMap == null || commitMap.isEmpty()) ? "FULL" : "PARTIAL",
                            commitMap == null ? 0 : commitMap.size());
                }
                return;
            } catch (Exception ex) {
                failedAttempt++;
                onCommitFail(ex, failedAttempt);

                final RetryDecision retryDecision = retryPolicy.evaluate(failedAttempt, ex);
                if (!retryDecision.shouldRetry()) {
                    if (log.isInfoEnabled()) {
                        log.info("Kafka commit retry exhausted. consumer={}, failedAttempts={}, mode={}",
                                consumerName(),
                                failedAttempt,
                                (commitMap == null || commitMap.isEmpty()) ? "FULL" : "PARTIAL");
                    }
                    return;
                }

                if (log.isDebugEnabled()) {
                    log.debug("Kafka commit retry scheduled. consumer={}, nextAttempt={}, backoffMs={}",
                            consumerName(),
                            failedAttempt + 1,
                            retryDecision.backoffMs());
                }

                if (!sleepBackoff(retryDecision.backoffMs(), "commit retry")) {
                    return;
                }
            }
        }
    }

    /**
     * 二쇨린?곸쑝濡?consumer lag瑜??섑뵆留곹빀?덈떎.
     */
    private void sampleLagIfNeeded(final KafkaConsumer<String, T> runningConsumer) {
        final long intervalMs = lagSampleIntervalMs();
        if (intervalMs <= 0) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now - lastLagSampleAt < intervalMs) {
            return;
        }
        lastLagSampleAt = now;

        try {
            final var assignment = runningConsumer.assignment();
            if (assignment.isEmpty()) {
                return;
            }
            final Map<TopicPartition, Long> endOffsets = runningConsumer.endOffsets(assignment);
            for (TopicPartition topicPartition : assignment) {
                final long position = runningConsumer.position(topicPartition);
                final long endOffset = endOffsets.getOrDefault(topicPartition, position);
                onLagSample(topicPartition, Math.max(0L, endOffset - position));
            }
        } catch (Exception ignored) {
            // lag ?섑뵆留??ㅽ뙣??泥섎━ ?먮쫫??以묐떒?섏? ?딆뒿?덈떎.
        }
    }

    /**
     * ?덉퐫???ㅽ뙣 ???ъ떆???꾨왂(seek + backoff)???섑뻾?⑸땲??
     */
    private void handleFailedRecordRetry(
            final KafkaConsumer<String, T> runningConsumer,
            final ConsumerRecord<String, T> failedRecord
    ) {
        if (failedRecord == null) {
            return;
        }

        if (retryFailedRecordFromCurrentOffset()) {
            final TopicPartition topicPartition = new TopicPartition(failedRecord.topic(), failedRecord.partition());
            runningConsumer.seek(topicPartition, failedRecord.offset());
            if (log.isDebugEnabled()) {
                log.debug("Kafka consumer seek to failed record. consumer={}, topic={}, partition={}, offset={}",
                        consumerName(),
                        failedRecord.topic(),
                        failedRecord.partition(),
                        failedRecord.offset());
            }
        }

        final long retryBackoffMs = failedRecordRetryBackoffMs();
        if (retryBackoffMs > 0L) {
            sleepBackoff(retryBackoffMs, "failed record retry");
        }
    }

    /**
     * commit ?ъ떆???뺤콉???앹꽦?⑸땲??
     *
     * <p>{@code commitRetryMax}??"?ъ떆???잛닔" ?섎??대?濡?
     * 怨듯넻 ?뺤콉??{@code maxAttempts(珥??쒕룄 ?잛닔)}??留욎텛湲??꾪빐 +1 蹂댁젙?⑸땲??</p>
     *
     * @return 怨좎젙 諛깆삤??commit ?ъ떆???뺤콉
     */
    private RetryPolicy createCommitRetryPolicy() {
        final int maxAttempts = Math.max(1, commitRetryMax() + 1);
        final long backoffMs = Math.max(0L, commitRetryBackoffMs());
        return new FixedRetryPolicy(maxAttempts, backoffMs);
    }

    /**
     * backoff ?湲곕? ?섑뻾?⑸땲??
     *
     * @param backoffMs ?湲??쒓컙(ms)
     * @param reason 濡쒓렇 異쒕젰???湲??ъ쑀
     * @return ?명꽣?쏀듃 ?놁씠 ?뺤긽 ?湲??꾨즺 ??true
     */
    private boolean sleepBackoff(final long backoffMs, final String reason) {
        if (backoffMs <= 0L) {
            return true;
        }
        try {
            Thread.sleep(backoffMs);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (log.isInfoEnabled()) {
                log.info("Kafka consumer backoff wait interrupted. consumer={}, reason={}, backoffMs={}",
                        consumerName(),
                        reason,
                        backoffMs);
            }
            return false;
        }
    }

    /**
     * ?꾩옱 consumer 李몄“瑜??ㅻ깄?룹쑝濡?媛?몄샃?덈떎.
     */
    private synchronized KafkaConsumer<String, T> snapshotConsumer() {
        return consumer;
    }

    /**
     * consumer瑜??덉쟾?섍쾶 ?レ뒿?덈떎.
     */
    private synchronized void closeConsumerQuietly() {
        final KafkaConsumer<String, T> current = consumer;
        consumer = null;
        if (current != null) {
            current.close();
        }
    }

    /**
     * 鍮꾨룞湲?紐⑤뱶?먯꽌 poll ?ㅻ젅?쒓? 愿由ы븯???곹깭 而⑦뀒?대꼫?낅땲??
     */
    private static final class AsyncProcessingContext {

        private final ExecutorService workerExecutor;
        private final AckQueue ackQueue = new AckQueue();
        private final PartitionCommitCoordinator commitCoordinator = new PartitionCommitCoordinator();
        private final List<AckEvent> ackDrainBuffer = new ArrayList<>();
        private final Set<TopicPartition> knownAssignments = new HashSet<>();
        private final AtomicInteger inFlightCount = new AtomicInteger();
        private final int ackDrainMaxBatch;
        private final int maxInFlightRecords;
        private boolean paused = false;

        private AsyncProcessingContext(
                final ExecutorService workerExecutor,
                final int ackDrainMaxBatch,
                final int maxInFlightRecords
        ) {
            this.workerExecutor = workerExecutor;
            this.ackDrainMaxBatch = ackDrainMaxBatch;
            this.maxInFlightRecords = maxInFlightRecords;
        }
    }

    /**
     * worker thread ?대쫫 suffix ?앹꽦???꾪븳 ?쒗?ㅼ엯?덈떎.
     */
    private static final AtomicInteger contextWorkerIndex = new AtomicInteger(0);
}

