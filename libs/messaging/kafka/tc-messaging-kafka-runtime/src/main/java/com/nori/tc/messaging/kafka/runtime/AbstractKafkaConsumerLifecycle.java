package com.nori.tc.messaging.kafka.runtime;

import com.nori.tc.common.consumer.runtime.AckEvent;
import com.nori.tc.common.consumer.runtime.AckQueue;
import com.nori.tc.common.consumer.runtime.AckStatus;
import com.nori.tc.common.consumer.runtime.FixedRetryPolicy;
import com.nori.tc.common.consumer.runtime.PartitionCommitCoordinator;
import com.nori.tc.common.consumer.runtime.RetryDecision;
import com.nori.tc.common.consumer.runtime.RetryPolicy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import com.nori.tc.messaging.kafka.runtime.commit.KafkaCommitOffsetMapper;
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
 * Kafka Consumer의 생명주기, poll 루프, ACK/커밋 처리, 비동기 처리 보조 기능을 공통화한 추상 클래스입니다.
 *
 * <p>하위 구현체는 주로 다음 책임만 구현하면 됩니다.</p>
 * <p>1) Consumer 생성 속성 제공</p>
 * <p>2) 구독/할당 모드와 대상 토픽/파티션 정의</p>
 * <p>3) 단일 레코드 처리 로직 구현</p>
 *
 * <p>커밋 추적은 중립 공용 모듈({@code tc-common-consumer-runtime})의 ACK/커밋 추적 모델을 사용하고,
 * 실제 Kafka 커밋 맵 변환은 {@link KafkaCommitOffsetMapper}에서 수행합니다.</p>
 *
 * @param <T> Kafka Consumer value 타입
 */
public abstract class AbstractKafkaConsumerLifecycle<T> implements Runnable, SmartLifecycle {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private volatile boolean running = false;
    private volatile long lastLagSampleAt = 0L;
    private KafkaConsumer<String, T> consumer;
    private Thread workerThread;

    /**
     * KafkaConsumer 생성에 필요한 속성 맵을 반환합니다.
     *
     * @return KafkaConsumer 생성 속성
     */
    protected abstract Map<String, Object> consumerProperties();

    /**
     * Consumer 바인딩 모드(SUBSCRIBE/ASSIGN)를 반환합니다.
     *
     * @return Consumer 바인딩 모드
     */
    protected abstract KafkaConsumerBindingMode bindingMode();

    /**
     * SUBSCRIBE 모드에서 사용할 토픽 목록을 반환합니다.
     *
     * @return 구독 대상 토픽 목록
     */
    protected List<String> subscribeTopics() {
        return List.of();
    }

    /**
     * ASSIGN 모드에서 사용할 고정 파티션 목록을 반환합니다.
     *
     * @return 직접 할당할 파티션 목록
     */
    protected List<TopicPartition> assignedPartitions() {
        return List.of();
    }

    /**
     * poll 호출 타임아웃을 반환합니다.
     *
     * @return poll 타임아웃
     */
    protected abstract Duration pollTimeout();

    /**
     * Consumer worker 스레드 이름을 반환합니다.
     *
     * @return worker 스레드 이름
     */
    protected abstract String threadName();

    /**
     * 종료 시 worker thread join 최대 대기 시간을 반환합니다.
     *
     * @return 종료 대기 시간(ms)
     */
    protected abstract long shutdownWaitMs();

    /**
     * 커밋 재시도 최대 횟수를 반환합니다.
     *
     * @return 커밋 재시도 최대 횟수
     */
    protected abstract int commitRetryMax();

    /**
     * 커밋 재시도 간 백오프 시간을 반환합니다.
     *
     * @return 커밋 재시도 백오프(ms)
     */
    protected abstract long commitRetryBackoffMs();

    /**
     * Consumer lag 샘플링 주기를 반환합니다.
     *
     * @return lag 샘플링 주기(ms)
     */
    protected abstract long lagSampleIntervalMs();

    /**
     * 레코드 처리를 poll 스레드와 분리할지 여부를 반환합니다.
     *
     * @return 비동기 레코드 처리 활성화 여부
     */
    protected boolean asyncRecordProcessingEnabled() {
        return false;
    }

    /**
     * 비동기 레코드 처리 활성화 시 worker 스레드 수를 반환합니다.
     *
     * @return worker 스레드 수
     */
    protected int recordWorkerThreads() {
        return 1;
    }

    /**
     * 한 번에 drain할 ACK 이벤트 최대 배치 수를 반환합니다.
     *
     * @return ACK drain 최대 배치 수
     */
    protected int ackDrainMaxBatch() {
        return 512;
    }

    /**
     * 비동기 처리 시 허용할 최대 in-flight 레코드 수를 반환합니다.
     *
     * @return 최대 in-flight 레코드 수
     */
    protected int maxInFlightRecords() {
        return 10_000;
    }

    /**
     * 단일 Kafka 레코드를 처리합니다.
     *
     * @param record 처리 대상 레코드
     */
    protected abstract void handleRecord(ConsumerRecord<String, T> record);

    /**
     * 로깅/메트릭 식별용 Consumer 이름을 반환합니다.
     *
     * @return Consumer 이름
     */
    protected String consumerName() {
        return getClass().getSimpleName();
    }

    /**
     * 커밋 실패 콜백입니다.
     *
     * <p>하위 구현체에서 메트릭/알람 연동 포인트로 재정의할 수 있습니다.</p>
     *
     * @param ex      실패 예외
     * @param attempt 실패 시도 횟수
     */
    protected void onCommitFail(final Exception ex, final int attempt) {
        log.warn("Kafka commit failed. consumer={}, attempt={}", consumerName(), attempt, ex);
    }

    /**
     * 레코드 처리 실패 콜백입니다.
     *
     * @param record 실패 레코드
     * @param ex     처리 예외
     */
    protected void onRecordFail(final ConsumerRecord<String, T> record, final Exception ex) {
        log.warn("Kafka record handling failed. consumer={}, topic={}, partition={}, offset={}",
                consumerName(), record.topic(), record.partition(), record.offset(), ex);
    }

    /**
     * lag 샘플링 결과 콜백입니다.
     *
     * @param topicPartition 샘플링 대상 파티션
     * @param lag            계산된 lag 값
     */
    protected void onLagSample(final TopicPartition topicPartition, final long lag) {
        // no-op
    }

    /**
     * Consumer 생성/바인딩/worker 시작 직후 호출되는 훅 메서드입니다.
     *
     * @param startedConsumer 시작 완료된 Consumer 인스턴스
     */
    protected void afterStart(final KafkaConsumer<String, T> startedConsumer) {
        // no-op
    }

    /**
     * 레코드 처리 실패 시에도 현재 poll 배치를 커밋 대상으로 볼지 여부를 반환합니다.
     *
     * @return 실패 레코드 발생 시 커밋 진행 여부
     */
    protected boolean commitOnRecordFailure() {
        return true;
    }

    /**
     * 실패 레코드 발생 시 현재 오프셋으로 seek 재시도할지 여부를 반환합니다.
     *
     * @return 실패 레코드 현재 오프셋 재시도 여부
     */
    protected boolean retryFailedRecordFromCurrentOffset() {
        return false;
    }

    /**
     * 실패 레코드 재시도 전 대기 시간을 반환합니다.
     *
     * @return 실패 레코드 재시도 백오프(ms)
     */
    protected long failedRecordRetryBackoffMs() {
        return 0L;
    }

    /**
     * Kafka Consumer 생성, 바인딩, worker 스레드 시작을 수행합니다.
     *
     * <p>이미 실행 중이면 중복 시작을 방지하고 debug 로그만 남깁니다.</p>
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
     * Kafka Consumer 중지를 요청하고 worker 스레드 종료를 대기한 뒤 Consumer를 닫습니다.
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
     * 현재 실행 여부를 반환합니다.
     *
     * @return 실행 중 여부
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Spring SmartLifecycle phase 값을 반환합니다.
     *
     * @return lifecycle phase (기본값 0)
     */
    @Override
    public int getPhase() {
        return 0;
    }

    /**
     * Consumer poll 루프 본체입니다.
     *
     * <p>동기/비동기 처리 모드를 모두 지원하며, poll -> 처리 -> ACK 수집 -> 커밋 -> lag 샘플링 흐름을
     * 반복 수행합니다.</p>
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
                        // poll 野껉퀗?드첎? ??쑴堉??worker ack???袁⑹읅??????됱몵沃샕嚥?雅뚯눊由?怨몄몵嚥???뺤쟿?紐낅???덈뼄.
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
                context.commitCoordinator.registerPartitionIfAbsent(topicPartition.topic(), topicPartition.partition(), initialOffset);
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
            context.commitCoordinator.unregisterPartition(topicPartition.topic(), topicPartition.partition());
            context.knownAssignments.remove(topicPartition);
            if (log.isDebugEnabled()) {
                log.debug("Commit tracker unregistered from assignment. consumer={}, topicPartition={}",
                        consumerName(), topicPartition);
            }
        }
    }

    
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

    
    private void submitRecordsAsync(final ConsumerRecords<String, T> records, final AsyncProcessingContext context) {
        if (records.isEmpty()) {
            return;
        }

        for (ConsumerRecord<String, T> record : records) {
            final TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
            context.commitCoordinator.registerPartitionIfAbsent(topicPartition.topic(), topicPartition.partition(), record.offset());
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
     * worker가 적재한 ACK 이벤트를 배치로 수집하고 커밋 가능한 오프셋만 계산/커밋합니다.
     *
     * <p>중립 공용 런타임에서 계산한 커밋 계획은 {@link KafkaCommitOffsetMapper}를 통해 Kafka 커밋 맵으로
     * 변환한 뒤 실제 commitWithRetry를 호출합니다.</p>
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

        final var commitPlan = context.commitCoordinator.collectCommitOffsets();
        final Map<TopicPartition, OffsetAndMetadata> commitMap = KafkaCommitOffsetMapper.toKafkaCommitMap(commitPlan);
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

    
    private void handleNonCommittableAck(
            final KafkaConsumer<String, T> runningConsumer,
            final AckEvent event
    ) {
        if (retryFailedRecordFromCurrentOffset()) {
            runningConsumer.seek(new TopicPartition(event.topic(), event.partition()), event.offset());
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
        // 筌ㅼ뮇伊???뺤쟿?紐꾩뱽 ??甕?????묐뻬???袁⑥뵭 ?뚣끇而?揶쎛?關苑??餓κ쑴???덈뼄.
        drainAckAndCommit(runningConsumer, context);
    }

    
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
     * Kafka 커밋을 재시도 정책 기반으로 수행합니다.
     *
     * <p>commitMap이 비어 있으면 전체 커밋(commitSync), 값이 있으면 부분 커밋(commitSync(map))을 수행합니다.</p>
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
            // lag ??묐탣筌???쎈솭??筌ｌ꼶???癒?カ??餓λ쵎???? ??녿뮸??덈뼄.
        }
    }

    
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

    
    private RetryPolicy createCommitRetryPolicy() {
        final int maxAttempts = Math.max(1, commitRetryMax() + 1);
        final long backoffMs = Math.max(0L, commitRetryBackoffMs());
        return new FixedRetryPolicy(maxAttempts, backoffMs);
    }

    
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

    
    private synchronized KafkaConsumer<String, T> snapshotConsumer() {
        return consumer;
    }

    
    private synchronized void closeConsumerQuietly() {
        final KafkaConsumer<String, T> current = consumer;
        consumer = null;
        if (current != null) {
            current.close();
        }
    }

    
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

    
    private static final AtomicInteger contextWorkerIndex = new AtomicInteger(0);
}


