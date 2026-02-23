package com.nori.tc.comm.gateway.lifecycle.service;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.gateway.comm.EquipmentChannel;
import com.nori.tc.comm.gateway.comm.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.comm.GatewayProcessingService;
import com.nori.tc.comm.gateway.config.props.GatewayLifecycleProperties;
import com.nori.tc.comm.gateway.context.model.EquipmentContext;
import com.nori.tc.comm.gateway.context.model.EquipmentDesiredState;
import com.nori.tc.comm.gateway.context.model.EquipmentRuntimeState;
import com.nori.tc.comm.gateway.context.port.EquipmentStatePersistencePort;
import com.nori.tc.comm.gateway.context.service.EquipmentContextRegistry;
import com.nori.tc.comm.gateway.lifecycle.model.EquipmentLifecycleEvent;
import com.nori.tc.comm.gateway.lifecycle.model.EquipmentLifecycleOutcome;
import com.nori.tc.comm.gateway.lifecycle.port.EquipmentLifecycleOutcomeListener;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogContext;
import com.nori.tc.common.mailbox.Mailbox;
import com.nori.tc.common.mailbox.MailboxScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 설비(eqpId) 단위 라이프사이클 전이를 직렬 처리하는 상태머신입니다.
 *
 * <p>3차 구조 분해 후 이 클래스는 {@code lifecycle.service} 계층에 위치하며,
 * 모델({@code lifecycle.model})/포트({@code lifecycle.port})/컨텍스트 서비스({@code context.service})를 조합해
 * 실제 전이 흐름을 실행합니다.</p>
 *
 * <p>핵심 목표는 다음과 같습니다.</p>
 * <p>1) START/END 요청을 비동기 ACCEPT 흐름으로 전환</p>
 * <p>2) CHANNEL CONNECTED/DISCONNECTED 이벤트로 전이 완료 시점 확정</p>
 * <p>3) stateVersion 기반으로 stale timeout 이벤트 무시</p>
 * <p>4) 외부 리스너로 전이 결과(outcome) 전달</p>
 */
@Service
public class EquipmentLifecycleStateMachine implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EquipmentLifecycleStateMachine.class);

    private final GatewayLifecycleProperties lifecycleProperties;
    private final EquipmentContextRegistry contextRegistry;
    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayProcessingService processingService;
    private final EquipmentStatePersistencePort statePersistencePort;
    private final EquipmentLifecycleOutcomeListener outcomeListener;

    /**
     * eqpId별 직렬 이벤트 처리를 보장하는 공유 스케줄러입니다.
     */
    private final MailboxScheduler<EquipmentLifecycleEvent> eventScheduler;

    /**
     * eqpId당 stateVersion 시퀀스입니다.
     */
    private final Map<String, AtomicLong> stateVersionSequenceByEqp = new ConcurrentHashMap<>();

    /**
     * eqpId당 최근에 허용된 stateVersion입니다.
     *
     * <p>오래된 시간 초과 이벤트를 무시하기 위한 기준으로 사용됩니다.</p>
     */
    private final Map<String, Long> latestStateVersionByEqp = new ConcurrentHashMap<>();

    /**
     * eqpId별 pending 전이 상태입니다.
     */
    private final Map<String, PendingTransition> pendingTransitionByEqp = new ConcurrentHashMap<>();

    private ExecutorService workerPool;
    private ScheduledExecutorService timeoutScheduler;
    private volatile boolean running = false;

    /**
     * 상태머신 의존 객체를 초기화합니다.
     */
    public EquipmentLifecycleStateMachine(
            final GatewayLifecycleProperties lifecycleProperties,
            final EquipmentContextRegistry contextRegistry,
            final EquipmentChannelRegistry channelRegistry,
            final GatewayProcessingService processingService,
            final ObjectProvider<EquipmentStatePersistencePort> statePersistencePortProvider,
            final ObjectProvider<EquipmentLifecycleOutcomeListener> outcomeListenerProvider
    ) {
        this.lifecycleProperties = Objects.requireNonNull(lifecycleProperties, "lifecycleProperties is null");
        this.contextRegistry = Objects.requireNonNull(contextRegistry, "contextRegistry is null");
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry is null");
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        this.statePersistencePort = statePersistencePortProvider.getIfAvailable(() -> EquipmentStatePersistencePort.NO_OP);
        this.outcomeListener = outcomeListenerProvider.getIfAvailable(EquipmentLifecycleOutcomeListener::noOp);
        this.eventScheduler = new MailboxScheduler<>(
                lifecycleProperties.getEventMailboxCapacity(),
                this::openEqpLogContext
        );

        if (log.isDebugEnabled()) {
            log.debug("EquipmentLifecycleStateMachine 의존성 초기화 완료. mailboxCapacity={}, defaultTimeoutMs={}",
                    lifecycleProperties.getEventMailboxCapacity(),
                    lifecycleProperties.getDefaultTimeoutMs());
        }
    }

    /**
     * START 요청을 비동기 상태머신 이벤트로 등록합니다.
     */
    public void requestStart(
            final String eqpId,
            final String traceId,
            final long timeoutMs
    ) {
        final String normalizedEqpId = normalizeEqpId(eqpId);
        final long stateVersion = nextStateVersion(normalizedEqpId);
        publish(EquipmentLifecycleEvent.startRequested(normalizedEqpId, traceId, stateVersion, normalizeTimeoutMs(timeoutMs)));
    }

    /**
     * END 요청을 비동기 상태머신 이벤트로 등록합니다.
     */
    public void requestEnd(
            final String eqpId,
            final String traceId,
            final long timeoutMs
    ) {
        final String normalizedEqpId = normalizeEqpId(eqpId);
        final long stateVersion = nextStateVersion(normalizedEqpId);
        publish(EquipmentLifecycleEvent.endRequested(normalizedEqpId, traceId, stateVersion, normalizeTimeoutMs(timeoutMs)));
    }

    /**
     * CHANNEL_CONNECTED 이벤트를 상태머신으로 전달합니다.
     */
    public void onChannelConnected(
            final String eqpId,
            final String traceId,
            final String reason
    ) {
        final String normalizedEqpId = normalizeEqpId(eqpId);
        publish(EquipmentLifecycleEvent.channelConnected(normalizedEqpId, traceId, reason));
    }

    /**
     * CHANNEL_DISCONNECTED 이벤트를 상태머신으로 전달합니다.
     */
    public void onChannelDisconnected(
            final String eqpId,
            final String traceId,
            final String reason
    ) {
        final String normalizedEqpId = normalizeEqpId(eqpId);
        publish(EquipmentLifecycleEvent.channelDisconnected(normalizedEqpId, traceId, reason));
    }

    /**
     * 현재 START pending 전이에 대해 외부 실패 신호로 종료를 요청합니다.
     *
     * <p>대표적으로 아웃바운드 연결 재시도 한계 소진처럼, lifecycle timeout까지
     * 기다릴 필요 없이 실패를 즉시 확정해야 할 때 사용합니다.</p>
     *
     * @param eqpId 대상 설비 ID
     * @param traceId 연관 traceId
     * @param reason 실패 사유 코드
     */
    public void onStartFailedIfPending(
            final String eqpId,
            final String traceId,
            final String reason
    ) {
        final String normalizedEqpId = normalizeEqpId(eqpId);
        final PendingTransition pending = pendingTransitionByEqp.get(normalizedEqpId);
        final long stateVersion = pending != null && pending.type() == PendingType.START
                ? pending.stateVersion()
                : 0L;
        publish(EquipmentLifecycleEvent.startFailed(normalizedEqpId, traceId, stateVersion, reason));
    }

    /**
     * lifecycle worker/timeout 스케줄러를 시작합니다.
     */
    @Override
    public synchronized void start() {
        if (running) {
            if (log.isDebugEnabled()) {
                log.debug("EquipmentLifecycleStateMachine start skipped because it is already running.");
            }
            return;
        }
        running = true;

        final int workerThreads = lifecycleProperties.getWorkerThreads();
        final int timeoutSchedulerThreads = lifecycleProperties.getTimeoutSchedulerThreads();
        workerPool = Executors.newFixedThreadPool(workerThreads, namedThreadFactory("gateway-lifecycle-worker-"));
        timeoutScheduler = Executors.newScheduledThreadPool(
                timeoutSchedulerThreads,
                namedThreadFactory("gateway-lifecycle-timeout-")
        );

        for (int i = 0; i < workerThreads; i++) {
            workerPool.execute(this::runWorkerLoop);
        }

        log.info(
                "EquipmentLifecycleStateMachine started. workerThreads={}, timeoutSchedulerThreads={}, mailboxCapacity={}, defaultTimeoutMs={}",
                workerThreads,
                timeoutSchedulerThreads,
                lifecycleProperties.getEventMailboxCapacity(),
                lifecycleProperties.getDefaultTimeoutMs()
        );
    }

    /**
     * lifecycle worker/timeout 스케줄러를 중지합니다.
     */
    @Override
    public synchronized void stop() {
        if (!running) {
            if (log.isDebugEnabled()) {
                log.debug("EquipmentLifecycleStateMachine stop skipped because it is already stopped.");
            }
            return;
        }
        running = false;

        if (workerPool != null) {
            workerPool.shutdownNow();
            workerPool = null;
        }
        if (timeoutScheduler != null) {
            timeoutScheduler.shutdownNow();
            timeoutScheduler = null;
        }

        log.info("EquipmentLifecycleStateMachine stopped.");
    }

    /**
     * 상태머신 실행 상태를 반환합니다.
     *
     * @return 실행 중이면 true
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Netty bootstrap(phase=0)보다 먼저 시작되도록 음수 phase를 사용합니다.
     */
    @Override
    public int getPhase() {
        return -100;
    }

    /**
     * lifecycle 이벤트를 scheduler mailbox에 적재합니다.
     *
     * <p>상태머신이 시작되기 전 들어온 이벤트는 유실 방지를 위해 즉시 처리합니다.</p>
     */
    private void publish(final EquipmentLifecycleEvent event) {
        Objects.requireNonNull(event, "event is null");
        withEqpLogContext(event.eqpId(), () -> {
            if (!running) {
                if (log.isDebugEnabled()) {
                    log.debug("Lifecycle event processed inline because state machine is not running yet. eqpId={}, eventType={}",
                            event.eqpId(),
                            event.eventType());
                }
                processEvent(event);
                return;
            }

            final boolean offered = eventScheduler.enqueue(event, System.currentTimeMillis());
            if (!offered) {
                log.warn("Lifecycle event mailbox overflow. processing inline. eqpId={}, eventType={}, stateVersion={}",
                        event.eqpId(),
                        event.eventType(),
                        event.stateVersion());
                processEvent(event);
            } else if (log.isDebugEnabled()) {
                log.debug("Lifecycle event enqueued. eqpId={}, eventType={}, stateVersion={}",
                        event.eqpId(),
                        event.eventType(),
                        event.stateVersion());
            }
        });
    }

    /**
     * worker 루프 본문입니다.
     */
    private void runWorkerLoop() {
        while (running) {
            try {
                final String eqpId = eventScheduler.takeReadyKey();
                final Mailbox<EquipmentLifecycleEvent> mailbox = eventScheduler.tryAcquire(eqpId);
                if (mailbox == null) {
                    if (log.isDebugEnabled()) {
                        withEqpLogContext(eqpId, () -> log.debug(
                                "Lifecycle worker skipped because mailbox acquire returned null. eqpId={}",
                                eqpId
                        ));
                    }
                    continue;
                }

                try {
                    withEqpLogContext(eqpId, () -> {
                        EquipmentLifecycleEvent event;
                        while ((event = mailbox.poll()) != null) {
                            processEvent(event);
                        }
                    });
                } finally {
                    eventScheduler.release(mailbox);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.error("Lifecycle worker loop error.", ex);
            }
        }
    }

    /**
     * 이벤트 타입별 상태 전이를 처리합니다.
     */
    private void processEvent(final EquipmentLifecycleEvent event) {
        if (event == null) {
            return;
        }
        try {
            switch (event.eventType()) {
                case START_REQUESTED -> handleStartRequested(event);
                case END_REQUESTED -> handleEndRequested(event);
                case CHANNEL_CONNECTED -> handleChannelConnected(event);
                case CHANNEL_DISCONNECTED -> handleChannelDisconnected(event);
                case START_TIMEOUT -> handleStartTimeout(event);
                case START_FAILED -> handleStartFailed(event);
                case END_TIMEOUT -> handleEndTimeout(event);
            }
        } catch (Exception ex) {
            log.error("Lifecycle event processing failed. eqpId={}, eventType={}, stateVersion={}",
                    event.eqpId(),
                    event.eventType(),
                    event.stateVersion(),
                    ex);
        }
    }

    /**
     * START 요청을 처리합니다.
     */
    private void handleStartRequested(final EquipmentLifecycleEvent event) {
        final String eqpId = event.eqpId();
        final long latestStateVersion = latestStateVersionByEqp.getOrDefault(eqpId, 0L);
        if (EquipmentLifecycleTransitionGuard.isStaleRequest(event.stateVersion(), latestStateVersion)) {
            if (log.isDebugEnabled()) {
                log.debug("Stale START request ignored. eqpId={}, incomingStateVersion={}, latestStateVersion={}",
                        eqpId,
                        event.stateVersion(),
                        latestStateVersion);
            }
            return;
        }

        latestStateVersionByEqp.put(eqpId, event.stateVersion());

        final EquipmentContext context = findContextOrNull(eqpId);
        if (context == null) {
            log.warn("START request ignored because equipment context is missing. eqpId={}, traceId={}",
                    eqpId,
                    event.traceId());
            return;
        }

        context.updateDesiredState(EquipmentDesiredState.STARTED, "LIFECYCLE_START_REQUESTED", event.traceId());

        if (isChannelActive(eqpId)) {
            context.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "LIFECYCLE_START_APPLIED", event.traceId());
            pendingTransitionByEqp.remove(eqpId);
            statePersistencePort.recordStart(
                    eqpId,
                    event.traceId(),
                    "Lifecycle start completed immediately (already connected)"
            );
            emitOutcome(EquipmentLifecycleOutcome.startApplied(
                    eqpId,
                    event.traceId(),
                    event.stateVersion(),
                    "ALREADY_CONNECTED"
            ));
            log.info("LIFECYCLE_TRANSITION_APPLIED. eqpId={}, transition=START, stateVersion={}, result=CONNECTED",
                    eqpId,
                    event.stateVersion());
            return;
        }

        context.updateRuntimeState(EquipmentRuntimeState.CONNECTING, "LIFECYCLE_START_ACCEPTED", event.traceId());
        pendingTransitionByEqp.put(
                eqpId,
                new PendingTransition(PendingType.START, event.stateVersion(), event.traceId(), event.timeoutMs())
        );
        scheduleTimeout(event, PendingType.START);

        log.info("LIFECYCLE_REQUEST_ACCEPTED. eqpId={}, transition=START, stateVersion={}, timeoutMs={}",
                eqpId,
                event.stateVersion(),
                event.timeoutMs());
    }

    /**
     * END 요청을 처리합니다.
     */
    private void handleEndRequested(final EquipmentLifecycleEvent event) {
        final String eqpId = event.eqpId();
        final long latestStateVersion = latestStateVersionByEqp.getOrDefault(eqpId, 0L);
        if (EquipmentLifecycleTransitionGuard.isStaleRequest(event.stateVersion(), latestStateVersion)) {
            if (log.isDebugEnabled()) {
                log.debug("Stale END request ignored. eqpId={}, incomingStateVersion={}, latestStateVersion={}",
                        eqpId,
                        event.stateVersion(),
                        latestStateVersion);
            }
            return;
        }

        latestStateVersionByEqp.put(eqpId, event.stateVersion());

        final EquipmentContext context = findContextOrNull(eqpId);
        if (context == null) {
            log.warn("END request ignored because equipment context is missing. eqpId={}, traceId={}",
                    eqpId,
                    event.traceId());
            return;
        }

        context.updateDesiredState(EquipmentDesiredState.ENDED, "LIFECYCLE_END_REQUESTED", event.traceId());

        if (!isChannelActive(eqpId)) {
            context.updateRuntimeState(EquipmentRuntimeState.DISCONNECTED, "LIFECYCLE_END_APPLIED", event.traceId());
            pendingTransitionByEqp.remove(eqpId);
            processingService.removeMailbox(eqpId);
            statePersistencePort.recordEnd(
                    eqpId,
                    event.traceId(),
                    "Lifecycle end completed immediately (already disconnected)"
            );
            emitOutcome(EquipmentLifecycleOutcome.endApplied(
                    eqpId,
                    event.traceId(),
                    event.stateVersion(),
                    "ALREADY_DISCONNECTED"
            ));
            log.info("LIFECYCLE_TRANSITION_APPLIED. eqpId={}, transition=END, stateVersion={}, result=DISCONNECTED",
                    eqpId,
                    event.stateVersion());
            return;
        }

        context.updateRuntimeState(EquipmentRuntimeState.STOPPING, "LIFECYCLE_END_ACCEPTED", event.traceId());
        pendingTransitionByEqp.put(
                eqpId,
                new PendingTransition(PendingType.END, event.stateVersion(), event.traceId(), event.timeoutMs())
        );
        scheduleTimeout(event, PendingType.END);

        log.info("LIFECYCLE_REQUEST_ACCEPTED. eqpId={}, transition=END, stateVersion={}, timeoutMs={}",
                eqpId,
                event.stateVersion(),
                event.timeoutMs());
    }

    /**
     * CHANNEL_CONNECTED 이벤트를 처리합니다.
     */
    private void handleChannelConnected(final EquipmentLifecycleEvent event) {
        final String eqpId = event.eqpId();
        final EquipmentContext context = findContextOrNull(eqpId);
        if (context == null) {
            if (log.isDebugEnabled()) {
                log.debug("CHANNEL_CONNECTED ignored because equipment context is missing. eqpId={}, reason={}",
                        eqpId,
                        event.reason());
            }
            return;
        }

        context.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "LIFECYCLE_CHANNEL_CONNECTED", event.traceId());
        final PendingTransition pending = pendingTransitionByEqp.get(eqpId);
        if (pending == null) {
            if (log.isDebugEnabled()) {
                log.debug("CHANNEL_CONNECTED applied without pending transition. eqpId={}, reason={}",
                        eqpId,
                        event.reason());
            }
            return;
        }

        if (pending.type() == PendingType.START) {
            pendingTransitionByEqp.remove(eqpId, pending);
            statePersistencePort.recordStart(
                    eqpId,
                    pending.traceId(),
                    "Lifecycle start completed by channel connected event"
            );
            emitOutcome(EquipmentLifecycleOutcome.startApplied(
                    eqpId,
                    pending.traceId(),
                    pending.stateVersion(),
                    event.reason()
            ));
            log.info("LIFECYCLE_TRANSITION_APPLIED. eqpId={}, transition=START, stateVersion={}, reason={}",
                    eqpId,
                    pending.stateVersion(),
                    event.reason());
            return;
        }

        // END pending 상태에서 CONNECTED 이벤트가 들어오면 의도와 반대 흐름이므로 경고 로그를 남깁니다.
        log.warn("CHANNEL_CONNECTED received while END transition is pending. eqpId={}, pendingStateVersion={}, reason={}",
                eqpId,
                pending.stateVersion(),
                event.reason());
    }

    /**
     * CHANNEL_DISCONNECTED 이벤트를 처리합니다.
     */
    private void handleChannelDisconnected(final EquipmentLifecycleEvent event) {
        final String eqpId = event.eqpId();
        final EquipmentContext context = findContextOrNull(eqpId);
        if (context == null) {
            if (log.isDebugEnabled()) {
                log.debug("CHANNEL_DISCONNECTED ignored because equipment context is missing. eqpId={}, reason={}",
                        eqpId,
                        event.reason());
            }
            return;
        }

        context.updateRuntimeState(EquipmentRuntimeState.DISCONNECTED, "LIFECYCLE_CHANNEL_DISCONNECTED", event.traceId());
        final PendingTransition pending = pendingTransitionByEqp.get(eqpId);
        if (pending == null) {
            if (log.isDebugEnabled()) {
                log.debug("CHANNEL_DISCONNECTED applied without pending transition. eqpId={}, reason={}",
                        eqpId,
                        event.reason());
            }
            return;
        }

        if (pending.type() == PendingType.END) {
            pendingTransitionByEqp.remove(eqpId, pending);
            processingService.removeMailbox(eqpId);
            statePersistencePort.recordEnd(
                    eqpId,
                    pending.traceId(),
                    "Lifecycle end completed by channel disconnected event"
            );
            emitOutcome(EquipmentLifecycleOutcome.endApplied(
                    eqpId,
                    pending.traceId(),
                    pending.stateVersion(),
                    event.reason()
            ));
            log.info("LIFECYCLE_TRANSITION_APPLIED. eqpId={}, transition=END, stateVersion={}, reason={}",
                    eqpId,
                    pending.stateVersion(),
                    event.reason());
            return;
        }

        // START pending 중 연결이 끊기면 timeout 또는 지연된 이벤트 흐름으로 판단하여 pending 유지합니다.
        if (log.isDebugEnabled()) {
            log.debug("CHANNEL_DISCONNECTED received while START transition is pending. eqpId={}, pendingStateVersion={}, reason={}",
                    eqpId,
                    pending.stateVersion(),
                    event.reason());
        }
    }

    /**
     * START timeout 이벤트를 처리합니다.
     */
    private void handleStartTimeout(final EquipmentLifecycleEvent event) {
        final String eqpId = event.eqpId();
        final PendingTransition pending = pendingTransitionByEqp.get(eqpId);
        if (pending == null || pending.type() != PendingType.START) {
            if (log.isDebugEnabled()) {
                log.debug("START timeout ignored because no START pending exists. eqpId={}, timeoutStateVersion={}",
                        eqpId,
                        event.stateVersion());
            }
            return;
        }
        if (!EquipmentLifecycleTransitionGuard.isMatchingTimeout(event.stateVersion(), pending.stateVersion())) {
            if (log.isDebugEnabled()) {
                log.debug("Stale START timeout ignored. eqpId={}, timeoutStateVersion={}, pendingStateVersion={}",
                        eqpId,
                        event.stateVersion(),
                        pending.stateVersion());
            }
            return;
        }

        if (isChannelActive(eqpId)) {
            // timeout 시점에 이미 connected면 성공 완료로 정합성을 맞춥니다.
            final EquipmentContext context = findContextOrNull(eqpId);
            if (context != null) {
                context.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "LIFECYCLE_START_TIMEOUT_RECOVERED", pending.traceId());
            }
            pendingTransitionByEqp.remove(eqpId, pending);
            statePersistencePort.recordStart(
                    eqpId,
                    pending.traceId(),
                    "Lifecycle start completed by timeout recovery (channel already connected)"
            );
            emitOutcome(EquipmentLifecycleOutcome.startApplied(
                    eqpId,
                    pending.traceId(),
                    pending.stateVersion(),
                    "TIMEOUT_RECOVERED"
            ));
            log.info("LIFECYCLE_TRANSITION_APPLIED. eqpId={}, transition=START, stateVersion={}, reason=TIMEOUT_RECOVERED",
                    eqpId,
                    pending.stateVersion());
            return;
        }

        final EquipmentContext context = findContextOrNull(eqpId);
        if (context != null) {
            context.updateRuntimeState(EquipmentRuntimeState.ERROR, "LIFECYCLE_START_TIMEOUT", pending.traceId());
        }
        pendingTransitionByEqp.remove(eqpId, pending);
        emitOutcome(EquipmentLifecycleOutcome.startFailed(
                eqpId,
                pending.traceId(),
                pending.stateVersion(),
                "START_TIMEOUT"
        ));

        log.warn("LIFECYCLE_TIMEOUT. eqpId={}, transition=START, stateVersion={}, timeoutMs={}",
                eqpId,
                pending.stateVersion(),
                pending.timeoutMs());
    }

    /**
     * 외부 신호로 전달된 START 실패 이벤트를 처리합니다.
     */
    private void handleStartFailed(final EquipmentLifecycleEvent event) {
        final String eqpId = event.eqpId();
        final PendingTransition pending = pendingTransitionByEqp.get(eqpId);
        if (pending == null || pending.type() != PendingType.START) {
            if (log.isDebugEnabled()) {
                log.debug("START failed signal ignored because no START pending exists. eqpId={}, reason={}",
                        eqpId,
                        event.reason());
            }
            return;
        }

        if (event.stateVersion() > 0L
                && !EquipmentLifecycleTransitionGuard.isMatchingTimeout(event.stateVersion(), pending.stateVersion())) {
            if (log.isDebugEnabled()) {
                log.debug("Stale START failed signal ignored. eqpId={}, failedStateVersion={}, pendingStateVersion={}, reason={}",
                        eqpId,
                        event.stateVersion(),
                        pending.stateVersion(),
                        event.reason());
            }
            return;
        }

        if (isChannelActive(eqpId)) {
            final EquipmentContext context = findContextOrNull(eqpId);
            if (context != null) {
                context.updateRuntimeState(
                        EquipmentRuntimeState.CONNECTED,
                        "LIFECYCLE_START_FAILED_RECOVERED",
                        pending.traceId()
                );
            }
            pendingTransitionByEqp.remove(eqpId, pending);
            statePersistencePort.recordStart(
                    eqpId,
                    pending.traceId(),
                    "Lifecycle start completed by external failure recovery (channel already connected)"
            );
            emitOutcome(EquipmentLifecycleOutcome.startApplied(
                    eqpId,
                    pending.traceId(),
                    pending.stateVersion(),
                    "FAILED_SIGNAL_RECOVERED"
            ));
            log.info("LIFECYCLE_TRANSITION_APPLIED. eqpId={}, transition=START, stateVersion={}, reason=FAILED_SIGNAL_RECOVERED",
                    eqpId,
                    pending.stateVersion());
            return;
        }

        final EquipmentContext context = findContextOrNull(eqpId);
        if (context != null) {
            context.updateRuntimeState(EquipmentRuntimeState.ERROR, "LIFECYCLE_START_FAILED_SIGNAL", pending.traceId());
        }

        pendingTransitionByEqp.remove(eqpId, pending);
        final String failureReason = (event.reason() == null || event.reason().isBlank())
                ? "START_FAILED"
                : event.reason();
        emitOutcome(EquipmentLifecycleOutcome.startFailed(
                eqpId,
                pending.traceId(),
                pending.stateVersion(),
                failureReason
        ));
        log.warn("LIFECYCLE_FAILED. eqpId={}, transition=START, stateVersion={}, reason={}",
                eqpId,
                pending.stateVersion(),
                failureReason);
    }

    /**
     * END timeout 이벤트를 처리합니다.
     */
    private void handleEndTimeout(final EquipmentLifecycleEvent event) {
        final String eqpId = event.eqpId();
        final PendingTransition pending = pendingTransitionByEqp.get(eqpId);
        if (pending == null || pending.type() != PendingType.END) {
            if (log.isDebugEnabled()) {
                log.debug("END timeout ignored because no END pending exists. eqpId={}, timeoutStateVersion={}",
                        eqpId,
                        event.stateVersion());
            }
            return;
        }
        if (!EquipmentLifecycleTransitionGuard.isMatchingTimeout(event.stateVersion(), pending.stateVersion())) {
            if (log.isDebugEnabled()) {
                log.debug("Stale END timeout ignored. eqpId={}, timeoutStateVersion={}, pendingStateVersion={}",
                        eqpId,
                        event.stateVersion(),
                        pending.stateVersion());
            }
            return;
        }

        final EquipmentContext context = findContextOrNull(eqpId);
        if (context != null) {
            if (isChannelActive(eqpId)) {
                context.updateRuntimeState(EquipmentRuntimeState.ERROR, "LIFECYCLE_END_TIMEOUT", pending.traceId());
            } else {
                context.updateRuntimeState(EquipmentRuntimeState.DISCONNECTED, "LIFECYCLE_END_TIMEOUT_RECOVERED", pending.traceId());
            }
        }

        if (!isChannelActive(eqpId)) {
            // timeout 도착 시점에 이미 disconnect 상태면 END를 정상 완료로 승격합니다.
            pendingTransitionByEqp.remove(eqpId, pending);
            processingService.removeMailbox(eqpId);
            statePersistencePort.recordEnd(
                    eqpId,
                    pending.traceId(),
                    "Lifecycle end completed by timeout recovery (channel already disconnected)"
            );
            emitOutcome(EquipmentLifecycleOutcome.endApplied(
                    eqpId,
                    pending.traceId(),
                    pending.stateVersion(),
                    "TIMEOUT_RECOVERED"
            ));
            log.info("LIFECYCLE_TRANSITION_APPLIED. eqpId={}, transition=END, stateVersion={}, reason=TIMEOUT_RECOVERED",
                    eqpId,
                    pending.stateVersion());
            return;
        }

        pendingTransitionByEqp.remove(eqpId, pending);
        emitOutcome(EquipmentLifecycleOutcome.endFailed(
                eqpId,
                pending.traceId(),
                pending.stateVersion(),
                "END_TIMEOUT"
        ));
        log.warn("LIFECYCLE_TIMEOUT. eqpId={}, transition=END, stateVersion={}, timeoutMs={}",
                eqpId,
                pending.stateVersion(),
                pending.timeoutMs());
    }

    /**
     * timeout 이벤트를 등록합니다.
     */
    private void scheduleTimeout(
            final EquipmentLifecycleEvent requestEvent,
            final PendingType pendingType
    ) {
        if (timeoutScheduler == null) {
            log.warn("Lifecycle timeout scheduler is not ready. eqpId={}, transition={}, stateVersion={}",
                    requestEvent.eqpId(),
                    pendingType,
                    requestEvent.stateVersion());
            return;
        }

        final long boundedTimeoutMs = normalizeTimeoutMs(requestEvent.timeoutMs());
        timeoutScheduler.schedule(() -> {
            if (pendingType == PendingType.START) {
                publish(EquipmentLifecycleEvent.startTimeout(
                        requestEvent.eqpId(),
                        requestEvent.traceId(),
                        requestEvent.stateVersion()
                ));
                return;
            }
            publish(EquipmentLifecycleEvent.endTimeout(
                    requestEvent.eqpId(),
                    requestEvent.traceId(),
                    requestEvent.stateVersion()
            ));
        }, boundedTimeoutMs, TimeUnit.MILLISECONDS);

        if (log.isDebugEnabled()) {
            log.debug("Lifecycle timeout scheduled. eqpId={}, transition={}, stateVersion={}, timeoutMs={}",
                    requestEvent.eqpId(),
                    pendingType,
                    requestEvent.stateVersion(),
                    boundedTimeoutMs);
        }
    }

    /**
     * lifecycle 전이 결과를 외부 리스너에 전달합니다.
     *
     * <p>리스너 실패가 상태머신 본처리에 영향을 주지 않도록 예외를 격리합니다.</p>
     *
     * @param outcome 전이 확정 결과
     */
    private void emitOutcome(final EquipmentLifecycleOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome is null");
        try {
            outcomeListener.onOutcome(outcome);
            if (log.isDebugEnabled()) {
                log.debug("Lifecycle outcome emitted. eqpId={}, transition={}, status={}, stateVersion={}, reason={}",
                        outcome.eqpId(),
                        outcome.transition(),
                        outcome.status(),
                        outcome.stateVersion(),
                        outcome.reason());
            }
        } catch (Exception ex) {
            log.error("Lifecycle outcome listener failed. eqpId={}, transition={}, status={}, stateVersion={}, reason={}",
                    outcome.eqpId(),
                    outcome.transition(),
                    outcome.status(),
                    outcome.stateVersion(),
                    outcome.reason(),
                    ex);
        }
    }

    /**
     * 현재 채널 active 여부를 확인합니다.
     */
    private boolean isChannelActive(final String eqpId) {
        final EquipmentChannel channel = channelRegistry.get(new EquipmentId(eqpId));
        return channel != null && channel.isActive();
    }

    /**
     * context를 조회하고 없으면 null을 반환합니다.
     */
    private EquipmentContext findContextOrNull(final String eqpId) {
        final Optional<EquipmentContext> optional = contextRegistry.find(eqpId);
        return optional.orElse(null);
    }

    /**
     * eqpId 문자열을 검증/정규화합니다.
     */
    private static String normalizeEqpId(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId is required");
        }
        return eqpId.trim();
    }

    /**
     * lifecycle worker/timeout 스레드의 이름을 부여하는 ThreadFactory를 생성합니다.
     *
     * @param prefix 스레드 이름 접두사
     * @return 이름이 지정된 ThreadFactory
     */
    private static ThreadFactory namedThreadFactory(final String prefix) {
        final AtomicInteger sequence = new AtomicInteger(0);
        return runnable -> {
            final Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName(prefix + sequence.incrementAndGet());
            return thread;
        };
    }

    /**
     * timeout 입력값을 보정합니다.
     */
    private long normalizeTimeoutMs(final long timeoutMs) {
        return timeoutMs <= 0L ? lifecycleProperties.getDefaultTimeoutMs() : timeoutMs;
    }

    /**
     * eqpId의 다음 stateVersion을 발급합니다.
     */
    private long nextStateVersion(final String eqpId) {
        return stateVersionSequenceByEqp
                .computeIfAbsent(eqpId, key -> new AtomicLong(0L))
                .incrementAndGet();
    }

    /**
     * eqpId 기반 로그 컨텍스트를 생성합니다.
     *
     * <p>MailboxScheduler의 routingKey=eqpId 로그가 EQP 로그 파일로 분리되도록 사용합니다.</p>
     *
     * @param eqpId 장비 ID
     * @return 해제 가능한 로그 컨텍스트
     */
    private AutoCloseable openEqpLogContext(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return NoOpCloseable.INSTANCE;
        }
        return GatewayLogContext.withEqpId(eqpId);
    }

    /**
     * eqpId MDC를 적용한 상태로 작업을 실행합니다.
     *
     * <p>범위 C 로그 분리 정책에 따라 상태머신 내부 처리 로그를 EQP 로그 파일로 라우팅합니다.</p>
     *
     * @param eqpId 설비 ID
     * @param task 실행 작업
     */
    private void withEqpLogContext(final String eqpId, final Runnable task) {
        if (task == null) {
            return;
        }
        if (eqpId == null || eqpId.isBlank()) {
            task.run();
            return;
        }
        try (GatewayLogContext ignored = GatewayLogContext.withEqpId(eqpId)) {
            task.run();
        }
    }

    /**
     * 보류 중인 전환 유형입니다.
     */
    private enum PendingType {
        START,
        END
    }

    /**
     * eqpId당 보류 중인 전환 메타데이터입니다.
     */
    private record PendingTransition(
            PendingType type,
            long stateVersion,
            String traceId,
            long timeoutMs
    ) {
    }

    /**
     * 무작동 종료 가능 구현.
     */
    private enum NoOpCloseable implements AutoCloseable {
        INSTANCE;

        @Override
        public void close() {
            // no-op
        }
    }
}
