package com.nori.tc.comm.gateway.lifecycle;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.gateway.comm.EquipmentChannel;
import com.nori.tc.comm.gateway.comm.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.comm.GatewayProcessingService;
import com.nori.tc.comm.gateway.context.EquipmentContext;
import com.nori.tc.comm.gateway.context.EquipmentContextRegistry;
import com.nori.tc.comm.gateway.context.EquipmentDesiredState;
import com.nori.tc.comm.gateway.context.EquipmentRuntimeState;
import com.nori.tc.comm.gateway.context.EquipmentStatePersistencePort;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 설비(eqpid) 단위 lifecycle 상태 전이를 직렬 처리하는 상태머신입니다.
 *
 * <p>핵심 목적:</p>
 * <p>1) START/END 요청을 비동기 ACCEPT 방식으로 전환</p>
 * <p>2) 채널 CONNECTED/DISCONNECTED 이벤트를 받아 완료 시점 확정</p>
 * <p>3) stateVersion 기반 stale timeout 이벤트 무시</p>
 */
@Service
public class EqpLifecycleStateMachine implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EqpLifecycleStateMachine.class);

    /**
     * eqpId별 lifecycle 이벤트 mailbox 용량입니다.
     *
     * <p>폭주 상황에서도 이벤트 드롭 없이 버퍼링하기 위해 데이터 plane보다 넉넉하게 둡니다.</p>
     */
    private static final int EVENT_MAILBOX_CAPACITY = 256;

    /**
     * lifecycle 이벤트 worker 스레드 개수입니다.
     *
     * <p>eqpId별 직렬성은 MailboxScheduler가 보장하므로 worker는 여러 개여도 안전합니다.</p>
     */
    private static final int WORKER_THREADS = 2;

    /**
     * timeout 값이 비정상(<=0)으로 들어올 때 사용할 기본값(ms)입니다.
     */
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final EquipmentContextRegistry contextRegistry;
    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayProcessingService processingService;
    private final EquipmentStatePersistencePort statePersistencePort;

    /**
     * eqpId별 이벤트 직렬 처리를 위한 공용 스케줄러입니다.
     */
    private final MailboxScheduler<EqpLifecycleEvent> eventScheduler = new MailboxScheduler<>(EVENT_MAILBOX_CAPACITY);

    /**
     * eqpId별 stateVersion 시퀀스입니다.
     */
    private final Map<String, AtomicLong> stateVersionSequenceByEqp = new ConcurrentHashMap<>();

    /**
     * eqpId별 최신 stateVersion입니다.
     *
     * <p>timeout 지연 도착 등 stale 이벤트를 무시하기 위한 기준값입니다.</p>
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
     * 상태머신 의존성을 초기화합니다.
     */
    public EqpLifecycleStateMachine(
            final EquipmentContextRegistry contextRegistry,
            final EquipmentChannelRegistry channelRegistry,
            final GatewayProcessingService processingService,
            final ObjectProvider<EquipmentStatePersistencePort> statePersistencePortProvider
    ) {
        this.contextRegistry = Objects.requireNonNull(contextRegistry, "contextRegistry is null");
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry is null");
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        this.statePersistencePort = statePersistencePortProvider.getIfAvailable(() -> EquipmentStatePersistencePort.NO_OP);
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
        publish(EqpLifecycleEvent.startRequested(normalizedEqpId, traceId, stateVersion, normalizeTimeoutMs(timeoutMs)));
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
        publish(EqpLifecycleEvent.endRequested(normalizedEqpId, traceId, stateVersion, normalizeTimeoutMs(timeoutMs)));
    }

    /**
     * 채널 CONNECTED 이벤트를 상태머신으로 전달합니다.
     */
    public void onChannelConnected(
            final String eqpId,
            final String traceId,
            final String reason
    ) {
        final String normalizedEqpId = normalizeEqpId(eqpId);
        publish(EqpLifecycleEvent.channelConnected(normalizedEqpId, traceId, reason));
    }

    /**
     * 채널 DISCONNECTED 이벤트를 상태머신으로 전달합니다.
     */
    public void onChannelDisconnected(
            final String eqpId,
            final String traceId,
            final String reason
    ) {
        final String normalizedEqpId = normalizeEqpId(eqpId);
        publish(EqpLifecycleEvent.channelDisconnected(normalizedEqpId, traceId, reason));
    }

    /**
     * lifecycle worker/timeout 스케줄러를 시작합니다.
     */
    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;

        workerPool = Executors.newFixedThreadPool(WORKER_THREADS);
        timeoutScheduler = Executors.newSingleThreadScheduledExecutor();

        for (int i = 0; i < WORKER_THREADS; i++) {
            workerPool.execute(this::runWorkerLoop);
        }

        log.info("EqpLifecycleStateMachine started. workerThreads={}, mailboxCapacity={}",
                WORKER_THREADS,
                EVENT_MAILBOX_CAPACITY);
    }

    /**
     * lifecycle worker/timeout 스케줄러를 중지합니다.
     */
    @Override
    public synchronized void stop() {
        running = false;

        if (workerPool != null) {
            workerPool.shutdownNow();
            workerPool = null;
        }
        if (timeoutScheduler != null) {
            timeoutScheduler.shutdownNow();
            timeoutScheduler = null;
        }

        log.info("EqpLifecycleStateMachine stopped.");
    }

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
    private void publish(final EqpLifecycleEvent event) {
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
    }

    /**
     * worker 루프 본문입니다.
     */
    private void runWorkerLoop() {
        while (running) {
            try {
                final String eqpId = eventScheduler.takeReadyKey();
                final Mailbox<EqpLifecycleEvent> mailbox = eventScheduler.tryAcquire(eqpId);
                if (mailbox == null) {
                    continue;
                }

                try {
                    EqpLifecycleEvent event;
                    while ((event = mailbox.poll()) != null) {
                        processEvent(event);
                    }
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
    private void processEvent(final EqpLifecycleEvent event) {
        try {
            switch (event.eventType()) {
                case START_REQUESTED -> handleStartRequested(event);
                case END_REQUESTED -> handleEndRequested(event);
                case CHANNEL_CONNECTED -> handleChannelConnected(event);
                case CHANNEL_DISCONNECTED -> handleChannelDisconnected(event);
                case START_TIMEOUT -> handleStartTimeout(event);
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
    private void handleStartRequested(final EqpLifecycleEvent event) {
        final String eqpId = event.eqpId();
        final long latestStateVersion = latestStateVersionByEqp.getOrDefault(eqpId, 0L);
        if (EqpLifecycleTransitionGuard.isStaleRequest(event.stateVersion(), latestStateVersion)) {
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
    private void handleEndRequested(final EqpLifecycleEvent event) {
        final String eqpId = event.eqpId();
        final long latestStateVersion = latestStateVersionByEqp.getOrDefault(eqpId, 0L);
        if (EqpLifecycleTransitionGuard.isStaleRequest(event.stateVersion(), latestStateVersion)) {
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
    private void handleChannelConnected(final EqpLifecycleEvent event) {
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
    private void handleChannelDisconnected(final EqpLifecycleEvent event) {
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
            log.info("LIFECYCLE_TRANSITION_APPLIED. eqpId={}, transition=END, stateVersion={}, reason={}",
                    eqpId,
                    pending.stateVersion(),
                    event.reason());
            return;
        }

        // START pending 중 연결이 끊기면 timeout 또는 재연결 흐름으로 판단하여 pending 유지합니다.
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
    private void handleStartTimeout(final EqpLifecycleEvent event) {
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
        if (!EqpLifecycleTransitionGuard.isMatchingTimeout(event.stateVersion(), pending.stateVersion())) {
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

        log.warn("LIFECYCLE_TIMEOUT. eqpId={}, transition=START, stateVersion={}, timeoutMs={}",
                eqpId,
                pending.stateVersion(),
                pending.timeoutMs());
    }

    /**
     * END timeout 이벤트를 처리합니다.
     */
    private void handleEndTimeout(final EqpLifecycleEvent event) {
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
        if (!EqpLifecycleTransitionGuard.isMatchingTimeout(event.stateVersion(), pending.stateVersion())) {
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
            log.info("LIFECYCLE_TRANSITION_APPLIED. eqpId={}, transition=END, stateVersion={}, reason=TIMEOUT_RECOVERED",
                    eqpId,
                    pending.stateVersion());
            return;
        }

        pendingTransitionByEqp.remove(eqpId, pending);
        log.warn("LIFECYCLE_TIMEOUT. eqpId={}, transition=END, stateVersion={}, timeoutMs={}",
                eqpId,
                pending.stateVersion(),
                pending.timeoutMs());
    }

    /**
     * timeout 이벤트를 등록합니다.
     */
    private void scheduleTimeout(
            final EqpLifecycleEvent requestEvent,
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
                publish(EqpLifecycleEvent.startTimeout(
                        requestEvent.eqpId(),
                        requestEvent.traceId(),
                        requestEvent.stateVersion()
                ));
                return;
            }
            publish(EqpLifecycleEvent.endTimeout(
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
     * timeout 입력값을 보정합니다.
     */
    private static long normalizeTimeoutMs(final long timeoutMs) {
        return timeoutMs <= 0L ? DEFAULT_TIMEOUT_MS : timeoutMs;
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
     * pending 전이 타입입니다.
     */
    private enum PendingType {
        START,
        END
    }

    /**
     * eqpId별 pending 전이 메타데이터입니다.
     */
    private record PendingTransition(
            PendingType type,
            long stateVersion,
            String traceId,
            long timeoutMs
    ) {
    }
}
